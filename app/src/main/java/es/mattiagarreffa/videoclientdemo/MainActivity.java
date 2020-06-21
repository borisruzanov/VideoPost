package es.mattiagarreffa.videoclientdemo;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.UploadTask;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_VIDEO_CAPTURE = 1;

    Button recordVideo, uploadVideo;
    VideoView videoView;

    Uri videoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission();

        recordVideo = findViewById(R.id.recordVideo);
        recordVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchTakeVideoIntent();
            }
        });

        videoView = findViewById(R.id.videoView);

        uploadVideo = findViewById(R.id.uploadVideo);
        uploadVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadVideo();
            }
        });
    }

    private void dispatchTakeVideoIntent() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE); // Prepare intent to open the camera in video mode.
        takeVideoIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30); // Controls the camera record maximum time. The number reference to seconds.
        takeVideoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0); // Controls the video quality 0 = low quality 1 = high quality.
        startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE); // Launch the intent.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_OK) {
            /*
            If there is no problem in the video capture we get the videoView to show the recorded video
            in looping mode.
             */
            videoUri = intent.getData();
            videoView.setVideoURI(videoUri);

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                }
            });
            videoView.start();
        } else if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_CANCELED) {
            Toast.makeText(getApplicationContext(), "An error occurred, please try again later.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * This function uploads the video to Firebase Storage and creates a reference in the database.
     */
    private void uploadVideo() {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setIcon(R.mipmap.ic_launcher_round);
        progressDialog.setCancelable(false);
        progressDialog.setTitle("Uploading ...");
        progressDialog.show();
        if (videoUri != null) {
            final String videoID = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());

            UploadTask uploadTask = FirebaseStorage.getInstance().getReference().child("/videos/" + videoID).putFile(videoUri);
            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    taskSnapshot.getStorage().getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            HashMap<String, String> videoInfo = new HashMap<>();
                            videoInfo.put("videoID", videoID);
                            videoInfo.put("videoURL", String.valueOf(uri));
                            FirebaseDatabase.getInstance().getReference().child("videos").child(videoID).setValue(videoInfo).addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    progressDialog.dismiss();
                                    Toast.makeText(getApplicationContext(), "VIDEO UPLOADED SUCCESSFULLY", Toast.LENGTH_LONG).show();
                                    videoUri = null;
                                    videoView.setVideoURI(null);
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    progressDialog.dismiss();
                                    FirebaseStorage.getInstance().getReference().child("/videos/" + videoID).delete();
                                    Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            progressDialog.dismiss();
                            FirebaseStorage.getInstance().getReference().child("/videos/" + videoID).delete();
                            Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();

                        }
                    });
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    progressDialog.dismiss();
                    Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "PERMISSION TO USE CAMERA DENIED", Toast.LENGTH_LONG).show();
            MainActivity.this.finish();
        }
    }
}