package com.example.mobileproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText emailField, passwordField, nameField; // Added nameField
    private Button registerBtn;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // --- My changes start here ---
        emailField = findViewById(R.id.emailField);
        nameField = findViewById(R.id.nameField); // Initialize nameField
        passwordField = findViewById(R.id.passwordField);
        registerBtn = findViewById(R.id.registerBtn);
        progressBar = findViewById(R.id.progressBar);
        // --- My changes end here ---

        registerBtn.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        // --- My changes start here ---
        String email = emailField.getText().toString().trim();
        String name = nameField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        // --- My changes end here ---

        progressBar.setVisibility(View.VISIBLE);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // --- My changes start here ---
                        String userId = mAuth.getCurrentUser().getUid();

                        // Create the user map with all the details
                        Map<String, Object> user = new HashMap<>();
                        user.put("uid", userId);
                        user.put("name", name);
                        user.put("email", email);
                        user.put("points", 0);
                        user.put("photoUrl", "https://upload.wikimedia.org/wikipedia/commons/a/a2/Bubble_Tea.png");
                        user.put("joinedAt", FieldValue.serverTimestamp());

                        // Save the user data to Firestore
                        db.collection("users").document(userId)
                                .set(user)
                                .addOnSuccessListener(aVoid -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    Log.w("RegisterActivity", "Error adding document", e);
                                    Toast.makeText(RegisterActivity.this, "Registration failed: could not save user data.", Toast.LENGTH_LONG).show();
                                });
                        // --- My changes end here ---
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(RegisterActivity.this, "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
