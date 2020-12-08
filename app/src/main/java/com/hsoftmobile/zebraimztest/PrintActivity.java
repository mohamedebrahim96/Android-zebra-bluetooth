package com.hsoftmobile.zebraimztest;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.graphics.ZebraImageFactory;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Set;

public class PrintActivity extends AppCompatActivity {

	public static final int REQ_LOAD_IMAGE = 1;
	public static final int REQ_ENABLE_BT = 2;

	private static final int BT_CLASS_PRINTER = 1664;

	private ImageView imagePhoto;

	private boolean photoLoaded, deviceHasBluetooth, bluetoothIsEnabled, printerFound;

	private String printerAddress;

	private BluetoothDevice selectedPrinter;

	// BT stuff
	private BluetoothAdapter bluetoothAdapter;
	private Connection connection;
	private ArrayList<BluetoothDevice> availablePrinters;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_print);

		// var init
		photoLoaded = false;
		deviceHasBluetooth = false;
		bluetoothIsEnabled = false;
		printerFound = false;
		printerAddress = "";

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter != null) {
			deviceHasBluetooth = true;
			bluetoothIsEnabled = bluetoothAdapter.isEnabled();
		}

		// bind views
		Button btnChoosePhoto = (Button) findViewById(R.id.btn_choose_photo);
		Button btnPrintPhoto = (Button) findViewById(R.id.btn_print_photo);
		Button btnPrintText = (Button) findViewById(R.id.btn_print_text);
		Button btnPaired = (Button) findViewById(R.id.btn_paired);
		final EditText textMessage = (EditText) findViewById(R.id.text_message);
		imagePhoto = (ImageView) findViewById(R.id.image_photo);

		// click listeners
		btnPrintText.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!canPrint()) {
					return;
				}
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
				startActivityForResult(loadPhoto, REQ_LOAD_IMAGE);
			}
		});
		btnPrintPhoto.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!canPrint()) {
					return;
				}
				if (!photoLoaded) {
					showToast("You need to load a picture first");
				} else {
					BitmapDrawable bitmapDrawable = (BitmapDrawable) imagePhoto.getDrawable();
					sendBitmapOverBluetooth(bitmapDrawable.getBitmap());
				}
			}
		});
		btnPaired.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!canUseBluetooth()) {
					return;
				}
				new PairedDevicesTask().execute();
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQ_LOAD_IMAGE) {
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
					//*/
					photoLoaded = true;
				} catch (IOException e) {
					showToast(e.getLocalizedMessage());
					e.printStackTrace();
				}
			}
		}
	}

	// Misc methods - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}

	private ProgressDialog createProgressDialog(String title, String message) {
		ProgressDialog progressDialog = new ProgressDialog(this);
		progressDialog.setTitle(title);
		progressDialog.setMessage(message);
		progressDialog.setIndeterminate(true);
		progressDialog.setCancelable(false);
		return progressDialog;
	}

	private boolean canUseBluetooth() {
		if (!deviceHasBluetooth) {
			showToast("Device has no Bluetooth");
			return false;
		}
		bluetoothIsEnabled = bluetoothAdapter.isEnabled();
		if (!bluetoothIsEnabled) {
			showToast("Please enable Bluetooth first");
			return false;
		}
		return true;
	}

	private boolean canPrint() {
		if (!printerFound) {
			showToast("You need to select a printer first");
			return false;
		}
		return true;
	}

	// iMZ Printer methods

	private class PairedDevicesTask extends AsyncTask<Void, Void, String> {

		private ProgressDialog pairedDialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			pairedDialog = createProgressDialog("Getting list of paired devices", "Please wait...");
			pairedDialog.show();

			availablePrinters = new ArrayList<>();
		}

		@Override
		protected String doInBackground(Void... voids) {
			String errorMsg = "";
			Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice device: pairedDevices) {
					//final int deviceClass = device.getBluetoothClass().getDeviceClass();
					final int majorDeviceClass = device.getBluetoothClass().getMajorDeviceClass();
					// select only possible printers
					if (majorDeviceClass == BluetoothClass.Device.Major.IMAGING) {
						availablePrinters.add(device);
					}
				}
				if (availablePrinters.size() == 0) {
					errorMsg = "No paired printers found";
				}
			}
			return errorMsg;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			pairedDialog.dismiss();
			if (!result.isEmpty()) {
				showToast(result);
			} else {
				if (availablePrinters.size() > 1) {
					showSelectPrinterDialog();
				} else {
					// set only printer found
					selectedPrinter = availablePrinters.get(0);
					printerFound = true;
					showToast("Selected printer:" + selectedPrinter.getName());
				}
			}
		}
	}

	private class PrintBitmapTask extends AsyncTask<Bitmap, Void, String> {

		private ProgressDialog progressPrintBitmap;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			progressPrintBitmap = createProgressDialog("Printing photo", "Please wait...");
			progressPrintBitmap.show();
		}

		@Override
		protected String doInBackground(Bitmap... bitmaps) {
			if (bitmaps.length != 1) return "Wrong argument size";
			String errorMsg = "";
			Connection thePrinterConn = new BluetoothConnection(selectedPrinter.getAddress());
			try {
				thePrinterConn.open();
				ZebraPrinter printer = ZebraPrinterFactory.getInstance(PrinterLanguage.CPCL, thePrinterConn);
				float targetWidth = bitmaps[0].getWidth();
				float targetHeight = bitmaps[0].getHeight();
				final float maxWidth = 380f;
				float scaleFactor;
				if (targetWidth > maxWidth) {
					scaleFactor = targetWidth / maxWidth;
					targetWidth = maxWidth;
					targetHeight = targetHeight / scaleFactor;
				} else if (targetWidth < maxWidth) {
					scaleFactor = maxWidth / targetWidth;
					targetWidth = maxWidth;
					targetHeight = targetHeight * scaleFactor;
				}
				printer.printImage(ZebraImageFactory.getImage(bitmaps[0]), 0, 10, (int) targetWidth, (int) targetHeight, false);
				thePrinterConn.close();
			} catch (ConnectionException | IOException e) {
				e.printStackTrace();
				errorMsg = e.getLocalizedMessage();
			}
			return errorMsg;
		}

		@Override
		protected void onPostExecute(String s) {
			super.onPostExecute(s);

			progressPrintBitmap.dismiss();
			if (!s.isEmpty()) {
				showToast(s);
			}
		}
	}

	private class PrintTextTask extends AsyncTask<String, Void, String> {

		private ProgressDialog progressPrintText;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			progressPrintText = createProgressDialog("Printing text", "Please wait...");
			progressPrintText.show();
		}

		@Override
		protected String doInBackground(String... strings) {
			String errorMsg = "";
			Connection thePrinterConn = new BluetoothConnection(selectedPrinter.getAddress());
			try {
				// Open the connection - physical connection is established here.
				thePrinterConn.open();
				String cpclString = "! 0 200 200 100 1\n" +
						"TEXT 4 0 30 40 " + strings[0].trim() + "\n" +
						"FORM\n" +
						"PRINT\n";
				// Send the data to printer as a byte array.
				thePrinterConn.write(cpclString.getBytes(Charset.defaultCharset()));

				// Make sure the data got to the printer before closing the connection
				Thread.sleep(500);

				// Close the connection to release resources.
				thePrinterConn.close();
			} catch (ConnectionException e) {
				e.printStackTrace();
				errorMsg = e.getLocalizedMessage();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			return errorMsg;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);

			progressPrintText.dismiss();
			if (!result.isEmpty()) {
				showToast(result);
			}
		}
	}

	private void showSelectPrinterDialog() {
		if (availablePrinters == null || availablePrinters.size() == 0) return;
		CharSequence[] printers = new CharSequence[availablePrinters.size()];
		for (int i = 0; i < availablePrinters.size(); i++) {
			printers[i] = availablePrinters.get(i).getName();
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Select a printer to use");
		builder.setItems(printers, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int which) {
				selectedPrinter = availablePrinters.get(which);
				showToast("Selected printer:" + selectedPrinter.getName());
			}
		});
	}

	private void sendTextOverBluetooth(String message) {
		new PrintTextTask().execute(message);
	}

	private void sendBitmapOverBluetooth(Bitmap bitmap) {
		new PrintBitmapTask().execute(bitmap);
	}

	// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
}
