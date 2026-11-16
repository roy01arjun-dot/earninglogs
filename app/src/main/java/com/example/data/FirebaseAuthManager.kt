package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class AuthUserState(
    val isAuthenticated: Boolean = false,
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val authProvider: String = "NONE", // "EMAIL", "GOOGLE"
    val photoUrl: String? = null
) {
    val initials: String
        get() {
            val source = displayName.ifBlank { email }
            val parts = source.split(" ", ".", "_", "@").filter { it.isNotBlank() }
            return when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                parts.size == 1 -> parts[0].take(1).uppercase()
                else -> "?"
            }
        }
}

sealed class AuthResult {
    data class Success(val user: AuthUserState, val message: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
    data object Cancelled : AuthResult()
}

/**
 * Real Firebase Authentication.
 *
 * What was wrong before:
 *  - getAuthInstance() always returned null (`FirebaseApp.getApps(null as? Context ?: return null)`),
 *    so Firebase was never used. Every "login" was a fake local account stored in
 *    SharedPreferences and the uid was invented from the email. Firestore security rules
 *    therefore rejected every write, and the error was swallowed and reported as "Synced".
 *  - Signing in with an unknown email silently created a local account.
 *  - Google sign-in and "anonymous" sign-in were fake.
 */
object FirebaseAuthManager {

    private const val TAG = "AuthManager"

    private val _userState = MutableStateFlow(AuthUserState())
    val userState: StateFlow<AuthUserState> = _userState.asStateFlow()

    private var auth: FirebaseAuth? = null
    private var initialized = false

    /** True when google-services.json was bundled and Firebase initialised. */
    var isCloudAvailable: Boolean = false
        private set

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        isCloudAvailable = try {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not configured: ${e.message}")
            false
        }
        if (!isCloudAvailable) return

        auth = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
        auth?.let { a ->
            publish(a.currentUser)
            a.addAuthStateListener { publish(it.currentUser) }
        }
        // Remove leftovers of the old fake local-account system.
        context.getSharedPreferences("auth_worker_credentials_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    val currentUid: String? get() = auth?.currentUser?.uid

    private fun publish(user: FirebaseUser?) {
        _userState.value = user?.toState() ?: AuthUserState()
    }

    private fun FirebaseUser.toState(): AuthUserState {
        val provider = providerData.map { it.providerId }.let { ids ->
            if (ids.contains(GoogleAuthProvider.PROVIDER_ID)) "GOOGLE" else "EMAIL"
        }
        return AuthUserState(
            isAuthenticated = true,
            uid = uid,
            email = email.orEmpty(),
            displayName = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore("@").orEmpty(),
            authProvider = provider,
            photoUrl = photoUrl?.toString()
        )
    }

    private fun notConfigured() = AuthResult.Error(
        "Cloud accounts aren't set up in this build. Add google-services.json to the app module " +
            "(see SETUP.md). You can keep using the app offline."
    )

    private fun validate(email: String, password: String): String? = when {
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email address."
        password.length < 6 -> "Password must be at least 6 characters."
        else -> null
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val a = auth ?: return notConfigured()
        val clean = email.trim().lowercase()
        validate(clean, password)?.let { return AuthResult.Error(it) }
        return try {
            val user = a.signInWithEmailAndPassword(clean, password).await().user
                ?: return AuthResult.Error("Sign in failed. Please try again.")
            publish(user)
            AuthResult.Success(_userState.value, "Welcome back, ${_userState.value.displayName}")
        } catch (e: Exception) {
            AuthResult.Error(friendlyMessage(e))
        }
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): AuthResult {
        val a = auth ?: return notConfigured()
        val clean = email.trim().lowercase()
        validate(clean, password)?.let { return AuthResult.Error(it) }
        val name = displayName.trim().ifEmpty { clean.substringBefore("@") }
        return try {
            val user = a.createUserWithEmailAndPassword(clean, password).await().user
                ?: return AuthResult.Error("Could not create the account.")
            runCatching {
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
                user.reload().await()
            }
            publish(a.currentUser ?: user)
            AuthResult.Success(_userState.value, "Account created. Welcome, $name")
        } catch (e: Exception) {
            AuthResult.Error(friendlyMessage(e))
        }
    }

    /**
     * Google sign-in through Credential Manager. Needs Google enabled in Firebase Console
     * (which generates the `default_web_client_id` string from google-services.json).
     */
    suspend fun signInWithGoogle(activity: Activity): AuthResult {
        val a = auth ?: return notConfigured()
        val resId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (resId == 0) {
            return AuthResult.Error(
                "Google sign-in isn't enabled for this Firebase project yet. Enable it in Firebase Console → " +
                    "Authentication → Sign-in method, then download a fresh google-services.json."
            )
        }
        val webClientId = activity.getString(resId)
        return try {
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val user = a.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
                    ?: return AuthResult.Error("Google sign-in failed.")
                publish(user)
                AuthResult.Success(_userState.value, "Signed in as ${_userState.value.email}")
            } else {
                AuthResult.Error("Unexpected credential type returned by Google.")
            }
        } catch (e: GetCredentialCancellationException) {
            AuthResult.Cancelled
        } catch (e: NoCredentialException) {
            AuthResult.Error("No Google account found on this device. Add one in system settings and try again.")
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google credential error", e)
            AuthResult.Error("Google sign-in failed: ${e.message ?: e.type}")
        } catch (e: Exception) {
            AuthResult.Error(friendlyMessage(e))
        }
    }

    suspend fun sendPasswordReset(email: String): Pair<Boolean, String> {
        val a = auth ?: return false to (notConfigured().message)
        val clean = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(clean).matches()) {
            return false to "Enter your email address first."
        }
        return try {
            a.sendPasswordResetEmail(clean).await()
            true to "Password reset link sent to $clean"
        } catch (e: Exception) {
            false to friendlyMessage(e)
        }
    }

    suspend fun signOut(context: Context) {
        runCatching { auth?.signOut() }
        _userState.value = AuthUserState()
        // Also clear Google's cached credential so the account picker shows next time.
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    fun friendlyMessage(e: Throwable): String = when (e) {
        is FirebaseAuthInvalidUserException -> "No account found with this email."
        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
        is FirebaseAuthUserCollisionException -> "An account already exists with this email. Try signing in."
        is FirebaseAuthWeakPasswordException -> "Choose a stronger password (at least 6 characters)."
        is FirebaseNetworkException -> "No internet connection. Check your network and try again."
        is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a moment and try again."
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "This sign-in method is disabled. Enable it in Firebase Console → Authentication."
            else -> e.localizedMessage ?: "Authentication failed."
        }
        else -> e.localizedMessage ?: "Something went wrong. Please try again."
    }
}
