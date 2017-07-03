package com.hsoftmobile.zebraimztest;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PrintActivity extends AppCompatActivity {

	public static final int RESULT_LOAD_IMAGE = 1;

	private ImageView imagePhoto;

	private boolean photoLoaded;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_print);

		// var init
		photoLoaded = false;

		// bind views
		Button btnChoosePhoto = (Button) findViewById(R.id.btn_choose_photo);
		Button btnPrintPhoto = (Button) findViewById(R.id.btn_print_photo);
		Button btnPrintText = (Button) findViewById(R.id.btn_print_text);
		final EditText textMessage = (EditText) findViewById(R.id.text_message);
		imagePhoto = (ImageView) findViewById(R.id.image_photo);

		// click listeners
		btnPrintText.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String message = textMessage.getText().toString().trim();
				if (!message.isEmpty()) {
					sendTextOverBluetooth(message);
				}
			}
		});
		btnChoosePhoto.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent loadPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
				startActivityForResult(loadPhoto, RESULT_LOAD_IMAGE);
			}
		});
		btnPrintPhoto.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!photoLoaded) {
					showToast("You need to load a picture first");
				} else {
					showToast("Printing coming soon!");
				}
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == RESULT_LOAD_IMAGE) {
			if (resultCode == RESULT_OK && data != null) {
				Uri uri = data.getData();
				try {
					Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
					//imagePhoto.setImageBitmap(bitmap);
					//* get resized bitmap bytes
					final float maxWidth = 400f;
					float targetWidth = bitmap.getWidth();
					float targetHeight = bitmap.getHeight();
					if (targetWidth > maxWidth) {
						float scaleFactor = targetWidth / maxWidth;
						targetWidth = maxWidth;
						targetHeight = targetHeight / scaleFactor;
					}
					Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, (int) targetWidth, (int) targetHeight, false);
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
					imagePhoto.setImageBitmap(resizedBitmap);
					photoLoaded = true;
					//*/
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	// Misc methods - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}

	// iMZ Printer methods

	private void sendTextOverBluetooth(String message) {
		showToast(message);
	}
}
