# Owner login screen and behavior

## Public surface
- A neutral entry label is shown in the general app navigation. Never display the words Owner, Admin, Master Login or an Owner role label publicly.
- The concealed/private login screen itself displays two fields: **User ID** and **Password**.
- Both fields are optional as a pair in this exact way: Password is mandatory; User ID may be blank.
- A correct provisioned permanent Owner password authenticates directly when User ID is blank.
- When User ID is supplied, it must match the deployment-configured Owner User ID.
- Every failed login path displays exactly: `Invalid User ID or Password.`
- The screen must not distinguish wrong User ID, wrong password, expired configuration, or unknown account.
- On success, navigate to the private dashboard only after the backend returns a valid Owner session.

## Password storage and rotation
- The previously chat-shared password strings are compromised and must not be used for production.
- Provision fresh Owner passwords by generating BCrypt hashes outside the repository and placing only hashes in the deployment secret `OWNER_PASSWORD_HASHES`.
- The backend imports the hashes once when the Owner password table is empty. The table stores BCrypt hashes only.
- The password change screen is available only with an active Owner bearer session. It requires the current password and a new password of at least 12 characters.
- Rotation retires all active password hashes and revokes all Owner sessions. The Owner must log in again.
- Never ship Owner passwords, hashes, backend secrets or Owner bearer tokens inside the Android APK.
