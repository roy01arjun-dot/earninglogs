package com.example.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Who the app currently believes you are. Derived from FirebaseAuth, never from local prefs. */
sealed interface AuthState {
    /** No Firebase user. The app is fully usable in this state - it just doesn't sync. */
    data object SignedOut : AuthState

    data class SignedIn(
        val uid: String,
        val email: String,
        val displayName: String,
        val photoUrl: String?
    ) : AuthState
}

sealed interface SignInResult {
    data class Success(val uid: String) : SignInResult
    /** User backed out of the Google sheet. Not an error worth showing. */
    data object Cancelled : SignInResult
    data class Failure(val message: String) : SignInResult
}

/**
 * Real Google sign-in via Credential Manager, exchanged for a Firebase credential.
 *
 * This replaces a dialog that presented three hardcoded email addresses and a six-digit code the
 * app generated, displayed on screen, and then compared against itself. Nothing in the old flow
 * ever reached Firebase, so `FirebaseAuth.currentUser` stayed null and every Firestore write was
 * rejected by the security rules and silently redirected to a local file.
 */
class AuthRepository(private val appContext: Context) {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val credentialManager: CredentialManager by lazy { CredentialManager.create(appContext) }

    val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser.toAuthState())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    fun currentState(): AuthState = auth.currentUser.toAuthState()

    fun currentUid(): String? = auth.currentUser?.uid

    /**
     * Must be called with an Activity context - Credential Manager needs to show UI.
     */
    suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        val serverClientId = appContext.getString(R.string.default_web_client_id)
        if (serverClientId.isBlank()) {
            return SignInResult.Failure(
                "Google sign-in is not configured for this build (missing web client id)."
            )
        }

        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        return try {
            val response = credentialManager.getCredential(activityContext, request)
            val credential = response.credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return SignInResult.Failure("Unexpected credential type returned by Google.")
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val uid = authResult.user?.uid
                ?: return SignInResult.Failure("Firebase returned no user after sign-in.")

            SignInResult.Success(uid)
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.Failure("No Google account is available on this device. Add one in Settings, then try again.")
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager failed", e)
            SignInResult.Failure(e.localizedMessage ?: "Google sign-in failed.")
        } catch (e: Exception) {
            Log.w(TAG, "Sign-in failed", e)
            SignInResult.Failure(e.localizedMessage ?: "Sign-in failed.")
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign-out failed", e)
        }
        // Clears the "sign in as..." state so the next sign-in shows the picker again.
        try {
            credentialManager.clearCredentialState(
                androidx.credentials.ClearCredentialStateRequest(
                    androidx.credentials.ClearCredentialStateRequest.TYPE_CLEAR_CREDENTIAL_STATE
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Clearing credential state failed", e)
        }
    }

    private fun com.google.firebase.auth.FirebaseUser?.toAuthState(): AuthState =
        if (this == null) {
            AuthState.SignedOut
        } else {
            AuthState.SignedIn(
                uid = uid,
                email = email.orEmpty(),
                displayName = displayName.orEmpty().ifBlank { email?.substringBefore("@").orEmpty() },
                photoUrl = photoUrl?.toString()
            )
        }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
