package com.example.mobileproject;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    // UI Components
    private EditText emailField, passwordField, firstNameField, lastNameField, birthdayField;
    private CircleImageView profileImageView;
    private Button registerBtn;
    private ProgressBar progressBar;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private final String[] presetImageUrls = {
            "https://img.freepik.com/free-vector/bearded-man-profile_24908-81067.jpg",
            "https://img.freepik.com/free-vector/man-seated-using-laptop_24908-82598.jpg",
            "https://img.freepik.com/free-vector/afro-woman-with-megaphone_24908-81403.jpg",
            "https://img.freepik.com/free-vector/man-seated-using-laptop_24908-82652.jpg",
            "https://img.freepik.com/free-vector/person-using-cellphone-illustration_24908-81884.jpg",
            "https://img.freepik.com/free-vector/redhead-woman-profile-style_24908-81561.jpg",
            "https://img.freepik.com/free-vector/man-profile-account-picture_24908-81754.jpg?w=740&q=80",
            "https://img.freepik.com/free-vector/woman-profile-account-picture_24908-81036.jpg?w=360",
            "https://img.freepik.com/free-vector/woman-head-profile_24908-81681.jpg",
            "https://img.freepik.com/free-vector/hands-lifting-flower-garden_24908-81724.jpg",
            "https://img.freepik.com/free-vector/flower-cute-illustration_24908-82888.jpg",
            "https://img.freepik.com/free-vector/snail-doodle-illustration_24908-82578.jpg",
            "https://img.freepik.com/free-vector/person-using-phone_24908-81116.jpg?w=360",
            "https://img.freepik.com/free-vector/fresh-strawberry-fruit-healthy_24908-81208.jpg",
            "https://img.freepik.com/free-vector/cute-chicken-standing_24908-81287.jpg",
            "https://img.freepik.com/free-vector/cute-rooster-standing_24908-81226.jpg",
            "https://img.freepik.com/premium-vector/blond-man-profile_24908-82724.jpg",
            "https://img.freepik.com/free-vector/man-wearing-sunglasses-profile_24908-82613.jpg?semt=ais_hybrid&w=740&q=80",
            "https://img.freepik.com/free-vector/pink-flamingo-bird_24908-81020.jpg"
    };
    private String selectedImageUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        firstNameField = findViewById(R.id.firstNameField);
        lastNameField = findViewById(R.id.lastNameField);
        birthdayField = findViewById(R.id.birthdayField);
        profileImageView = findViewById(R.id.profileImageView);
        registerBtn = findViewById(R.id.registerBtn);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupClickListeners() {
        registerBtn.setOnClickListener(v -> validateAndRegisterUser());
        birthdayField.setOnClickListener(v -> showDatePickerDialog());
        profileImageView.setOnClickListener(v -> showImagePickerDialog());
    }

    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_avatar_picker, null);
        builder.setView(dialogView).setTitle("Choose an Avatar");

        GridView gridView = dialogView.findViewById(R.id.avatarGridView);
        AvatarAdapter adapter = new AvatarAdapter(this, presetImageUrls);
        gridView.setAdapter(adapter);

        AlertDialog dialog = builder.create();

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            selectedImageUrl = presetImageUrls[position];
            Glide.with(this).load(selectedImageUrl).into(profileImageView);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showDatePickerDialog() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, (view, year1, monthOfYear, dayOfMonth) -> {
            String selectedDate = (monthOfYear + 1) + "/" + dayOfMonth + "/" + year1;
            birthdayField.setText(selectedDate);
        }, year, month, day).show();
    }

    private void validateAndRegisterUser() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();
        String firstName = firstNameField.getText().toString().trim();
        String lastName = lastNameField.getText().toString().trim();
        String birthday = birthdayField.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName) || TextUtils.isEmpty(birthday)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUrl == null) {
            Toast.makeText(this, "Please select an avatar", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        createUserInAuthAndFirestore(email, password, firstName, lastName, birthday, selectedImageUrl);
    }

    private void createUserInAuthAndFirestore(final String email, String password, final String firstName, final String lastName, final String birthday, final String photoUrl) {
        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String userId = mAuth.getCurrentUser().getUid();

                Map<String, Object> user = new HashMap<>();
                user.put("uid", userId);
                user.put("firstName", firstName);
                user.put("lastName", lastName);
                user.put("name", firstName + " " + lastName);
                user.put("email", email);
                user.put("birthday", birthday);
                user.put("photoUrl", photoUrl);
                user.put("points", 0);
                user.put("joinedAt", FieldValue.serverTimestamp());

                db.collection("users").document(userId).set(user).addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                }).addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Log.w(TAG, "Error adding document", e);
                    Toast.makeText(RegisterActivity.this, "Registration failed: could not save user data.", Toast.LENGTH_LONG).show();
                });
            } else {
                progressBar.setVisibility(View.GONE);
                Log.w(TAG, "createUserWithEmail:failure", task.getException());
                Toast.makeText(RegisterActivity.this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private class AvatarAdapter extends ArrayAdapter<String> {

        public AvatarAdapter(@NonNull Context context, String[] urls) {
            super(context, 0, urls);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            CircleImageView imageView = (convertView == null) ? new CircleImageView(getContext()) : (CircleImageView) convertView;
            if(convertView == null){
                imageView.setLayoutParams(new GridView.LayoutParams(250, 250));
            }

            Glide.with(getContext()).load(getItem(position)).into(imageView);
            return imageView;
        }
    }
}
