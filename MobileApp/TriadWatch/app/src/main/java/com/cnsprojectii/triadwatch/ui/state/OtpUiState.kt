package com.cnsprojectii.triadwatch.ui.state

data class OtpUiState(
    val isRequestingOtp: Boolean = false,
    val otpRequestError: String? = null,
    val otpRequestSuccessMessage: String? = null,
    val isVerifyingOtp: Boolean = false,
    val otpVerificationError: String? = null,
    val navigateToHome: Boolean = false
    // Add any other OTP related UI states, e.g., if OTP input is valid, etc.
)