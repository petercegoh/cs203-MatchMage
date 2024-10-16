package com.java.firebase.demo.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.java.firebase.demo.user.Exceptions.TooManyRequestsException;

import com.google.firebase.cloud.FirestoreClient;


@Service
public class UserService {
    private final Firestore firestore;
    private final FirebaseAuth firebaseAuth;

    public UserService(Firestore firestore, FirebaseAuth firebaseAuth) {
        this.firestore = firestore;
        this.firebaseAuth = firebaseAuth;
    }

    public String createUser(Register register) throws ExecutionException, InterruptedException, FirebaseAuthException{
        if (!isPasswordValid(register.getPassword()))
            throw new IllegalArgumentException("Password should be between 8-32 characters, at least 1 uppercase, 1 lowercase letter, 1 digit and 1 special character.");
        
        String uid = createAccountInAuth(register.getEmail(), register.getPassword());
        
        // Send email verification to the user
        sendVerificationEmail(uid);

        // setAdminAuthority(uid); // Uncomment to make the next registration an admin user.
        return uid;
    }

    public String createAccountInAuth(String email, String password) throws ExecutionException, InterruptedException, FirebaseAuthException {
        // Create an account in Firebase Authentication
        // Returns Unique ID (uid)
        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(password)
                .setEmailVerified(false);
        UserRecord userAuthRecord = firebaseAuth.createUser(request);
        return userAuthRecord.getUid();
    }

    public void setAdminAuthority(String uid) throws ExecutionException, InterruptedException, FirebaseAuthException {
        Map<String, Object> claims = new HashMap<>();
        claims.put("admin", true);
        // claims.put("player", true);
        firebaseAuth.setCustomUserClaims(uid, claims);
    }

    public void createUserDetails(User user, String uid)
            throws ExecutionException, InterruptedException, FirebaseAuthException, FirestoreException {
        if (!(user.getGender().equals("Male") || user.getGender().equals("Female")))
            throw new IllegalArgumentException("Gender must be 'Male' or 'Female'");
        if (!isBirthdayValid(user.getBirthday()))
            throw new IllegalArgumentException("Incorrect birthday format, format should be DD/MM/YYYY");
        if (!isUsernameValid(user.getUserName()))
            throw new IllegalArgumentException("Username should be between 3-32 characters long");
        if (!isUsernameUnique(user.getUserName()))
            throw new IllegalArgumentException("Username exists, please choose another username");
        
        firestore.collection("user").document(uid).set(user);
    }

    // FOR TESTING ONLY
    // public String createTestAccountInAuth(String email, String password) throws ExecutionException, InterruptedException, FirebaseAuthException {
    //     // Create an account in Firebase Authentication
    //     // Returns Unique ID (uid)
    //     UserRecord.CreateRequest request = new UserRecord.CreateRequest()
    //             .setEmail(email)
    //             .setPassword(password)
    //             .setEmailVerified(true);
    //     UserRecord userAuthRecord = firebaseAuth.createUser(request);
    //     return userAuthRecord.getUid();
    // }

    // // FOR TESTING ONLY
    // public void createTestUser(String userName) throws ExecutionException, InterruptedException, FirebaseAuthException{
    //     String uid = createTestAccountInAuth(userName + "@test.com", "1@Secured");
    //     User user = new User(userName, userName + " test", "12/12/2000", "Male");
    //     createUserDetails(user, uid);
        
    //     System.out.println("Created " + userName);
    // }

    
    // Method to send email verification
    public void sendVerificationEmail(String uid) {

        try {
            UserRecord user = firebaseAuth.getUser(uid);

            // Trigger the email verification
            String verificationLink = firebaseAuth.generateEmailVerificationLink(user.getEmail());

            System.out.println(verificationLink);

            // Create an instance of EmailService
            EmailService emailService = new EmailService();

            // Use the EmailService to send the verification email
            emailService.sendVerificationEmail(user.getEmail(), verificationLink);

        } catch (FirebaseAuthException e) {
            // Handle exceptions if sending the email fails
            e.printStackTrace();
        }
    }

    // Verify the Firebase ID token and decode it
    public String getIdToken(String bearerToken) throws FirebaseAuthException {
        try {
            // Extract the token (assuming it's in the format "Bearer <JWT>")
            if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
                throw new IllegalArgumentException("Invalid bearer token");
            }
            String idToken = bearerToken.substring(7); // Remove "Bearer " from the header

            // Verify the token and get the user's UID
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            return decodedToken.getUid();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public FirebaseToken verifyIdToken(String idToken) throws FirebaseAuthException {
        try {
            return firebaseAuth.verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            throw e;
        }
    }

    private static final String FIREBASE_API_KEY = "AIzaSyBItH-UkQG9U1UfRILfioF7K_VeEw_Zbjo";

    public String parseJSON(ResponseEntity<String> response, String fieldName) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonResponse = mapper.readTree(response.getBody());
        String field = jsonResponse.get(fieldName).asText();
        return field;
    }

    public boolean isEmailVerified(String idToken) throws Exception {
        FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
        UserRecord userRecord = firebaseAuth.getUser(decodedToken.getUid());
        return userRecord.isEmailVerified();
    }

    // Retrieves the idToken on login (email & password)
    public String login(Login login)
            throws ExecutionException, InterruptedException, JsonProcessingException, Exception {
        // Basic Validation
        if (login.getEmail() == null || !login.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (login.getPassword() == null || login.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        // Creates a JSON template to send to Firebase Auth Client
        String requestPayload = String.format("{\"email\":\"%s\",\"password\":\"%s\",\"returnSecureToken\":true}",
                login.getEmail(), login.getPassword());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create the request entity
        HttpEntity<String> entity = new HttpEntity<>(requestPayload, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_API_KEY,
                    HttpMethod.POST,
                    entity,
                    String.class);

            // Parse the response
            if (response.getStatusCode() == HttpStatus.OK) {
                String token = parseJSON(response, "idToken");
                if (!isEmailVerified(token)){
                    System.out.println("Email not verified");
                    throw new IllegalArgumentException("Please verify your account via your email to continue.");
                }
                return token;
            }
            throw new Exception("Something went wrong");
        } catch (HttpClientErrorException e) {
            // Handle HTTP client errors (4xx)
            if (e.getMessage().contains("too-many-requests")) {
                throw new TooManyRequestsException(
                        "Too many requests detected. Please try again later.");
            }
            if (e.getStatusCode() == HttpStatus.FORBIDDEN && e.getMessage().contains("blocked")) {
                throw new AccessDeniedException(e.getMessage());
            }
            // throw new IllegalArgumentException(e.getMessage());
            throw new IllegalArgumentException("Email or password is incorrect.");
        } catch (HttpServerErrorException e) {
            // Handle HTTP server errors (5xx)
            throw new HttpServerErrorException(e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    public void logoutUser(String uid) throws FirebaseAuthException {
        firebaseAuth.revokeRefreshTokens(uid);
    }

    public Boolean userExists(String uid) throws ExecutionException, InterruptedException {
        DocumentReference documentReference = firestore.collection("user").document(uid);
        ApiFuture<DocumentSnapshot> future = documentReference.get();
        DocumentSnapshot document = future.get();
        return document.exists();
    }

    // For this Firebase doc, the uid is the documentId.
    public User getUser(String uid) throws ExecutionException, InterruptedException, Exception {
        DocumentReference documentReference = firestore.collection("user").document(uid); // get the doc
        ApiFuture<DocumentSnapshot> future = documentReference.get();
        DocumentSnapshot document = future.get();
        User user;
        if (document.exists()) {
            user = document.toObject(User.class); // convert to object
            return user;
        }
        throw new IllegalArgumentException("User not found.");
    }

    public List<User> getAllUsers() throws ExecutionException, InterruptedException {
        Firestore dbFirestore = FirestoreClient.getFirestore();
        
        // Retrieve all documents from the "user" collection
        ApiFuture<QuerySnapshot> future = dbFirestore.collection("user").get();
        
        // QuerySnapshot contains all documents in the collection
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        
        // Create a list to hold the User objects
        List<User> users = new ArrayList<>();
        
        // Convert each document to a usr object and add it to the list
        for (DocumentSnapshot document : documents) {
            User user = document.toObject(User.class);
            users.add(user);
        }
        
        return users;
    }
    
    public User getPlayer(String userName) throws ExecutionException, InterruptedException {
        Firestore dbFirestore = FirestoreClient.getFirestore();
        
        // Query the "user" collection to find the document where the userName matches
        ApiFuture<QuerySnapshot> future = dbFirestore.collection("user")
                                                      .whereEqualTo("userName", userName)
                                                      .get();
        
        // Get the list of matching documents (should contain at most one result)
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        
        // Check if any document was found
        if (!documents.isEmpty()) {
            // Convert the first matching document to a User object
            return documents.get(0).toObject(User.class);
        } else {
            // Return null or handle the case where no user was found
            return null;
        }
    }
    

    public String getUserEmail(String uid) throws ExecutionException, InterruptedException, FirebaseAuthException {
        UserRecord userRecord = firebaseAuth.getUser(uid);
        return userRecord.getEmail();
    }

    // User only allowed to update gender, birthday and name 
    public String updateUser(User user, String uid) throws ExecutionException, InterruptedException {
        if (!(user.getGender().equals("Male") || user.getGender().equals("Female")))
            throw new IllegalArgumentException("Gender must be 'Male' or 'Female'");
        if (!isBirthdayValid(user.getBirthday()))
            throw new IllegalArgumentException("Incorrect birthday format, format should be DD/MM/YYYY");
        
        if (userExists(uid)) {
            ApiFuture<WriteResult> collectionsApiFuture = firestore.collection("user").document(uid).set(user);
            return collectionsApiFuture.get().getUpdateTime().toString();
        }
        throw new IllegalArgumentException("User not found.");
    }

    public String updatePassword(String newPassword, String uid)
            throws ExecutionException, InterruptedException, FirebaseAuthException {
        if (!isPasswordValid(newPassword))
            throw new IllegalArgumentException(
                    "Password should be between 8-32 characters, at least 1 uppercase, 1 lowercase letter, 1 digit and 1 special character.");
        UpdateRequest request = new UserRecord.UpdateRequest(uid).setPassword(newPassword);
        firebaseAuth.updateUser(request);
        return "Successfully updated password";
    }

    // For this Firebase doc, the uid is the documentId.
    public String deleteUser(String uid) throws ExecutionException, InterruptedException, FirebaseAuthException {
        firebaseAuth.deleteUser(uid);
        firestore.collection("user").document(uid).delete();
        return "Successfully deleted " + uid;
    }

    // Birthday date format checks DD/MM/YYYY
    public boolean isBirthdayValid(String birthday) {
        String regex = "^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/\\d{4}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher birthdayMatcher = pattern.matcher(birthday);
        return birthdayMatcher.matches();
    }

    // Password length & complexity check
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 32;
    private static final Pattern UPPER_CASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWER_CASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR = Pattern.compile("[^a-zA-Z0-9]");

    public boolean isPasswordValid(String password) {
        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            return false;
        }
        if (!UPPER_CASE.matcher(password).find()) {
            return false;
        }
        if (!LOWER_CASE.matcher(password).find()) {
            return false;
        }
        if (!DIGIT.matcher(password).find()) {
            return false;
        }
        if (!SPECIAL_CHAR.matcher(password).find()) {
            return false;
        }
        return true;
    }

    // Username checks
    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 32;

    public boolean isUsernameValid(String username) {
        if (username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
            return false;
        }
        return true;
    }

    // Checks if username exists.
    public boolean isUsernameUnique(String username) throws ExecutionException, InterruptedException {
        String normalizedUsername = username.toLowerCase();

        ApiFuture<QuerySnapshot> future = firestore.collection("user")
                .whereEqualTo("userName", normalizedUsername)
                .get();

        QuerySnapshot querySnapshot = future.get();
        List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();

        return documents.isEmpty();
    }
}
