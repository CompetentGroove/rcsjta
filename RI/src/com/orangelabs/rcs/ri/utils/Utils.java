/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.orangelabs.rcs.ri.utils;

import java.io.File;
import java.util.Date;
import java.util.Set;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.widget.Toast;

import com.orangelabs.rcs.ri.R;

/**
 * Utility functions
 * 
 * @author Jean-Marc AUFFRET
 */
public class Utils {
	/**
	 * Notification ID for single chat
	 */
	public static int NOTIF_ID_SINGLE_CHAT = 1000; 
	
	/**
	 * Notification ID for chat
	 */
	public static int NOTIF_ID_GROUP_CHAT = 1001; 

	/**
	 * Notification ID for file transfer
	 */
	public static int NOTIF_ID_FT = 1002; 

	/**
	 * Notification ID for image share
	 */
	public static int NOTIF_ID_IMAGE_SHARE = 1003; 

	/**
	 * Notification ID for video share
	 */
	public static int NOTIF_ID_VIDEO_SHARE = 1004; 

	/**
	 * Returns the application version from manifest file 
	 * 
	 * @param ctx Context
	 * @return Application version or null if not found
	 */
	public static String getApplicationVersion(Context ctx) {
		String version = null;
		try {
			PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
			version = info.versionName;
		} catch(NameNotFoundException e) {
		}
		return version;
	}
	
	/**
	 * Create a contact selector based on the native address book
	 * 
	 * @param activity Activity
	 * @return List adapter
	 */
	public static ContactListAdapter createContactListAdapter(Activity activity) {
	    String[] PROJECTION = new String[] {
	    		Phone._ID,
	    		Phone.NUMBER,
	    		Phone.LABEL,
	    		Phone.TYPE,
	    		Phone.CONTACT_ID
		    };
        ContentResolver content = activity.getContentResolver();
		Cursor cursor = content.query(Phone.CONTENT_URI, PROJECTION, Phone.NUMBER + "!='null'", null, null);

		// List of unique number
		Vector<String> treatedNumbers = new Vector<String>();
		
		MatrixCursor matrix = new MatrixCursor(PROJECTION);
		while (cursor.moveToNext()){
			// Key is phone number
			String phoneNumber = cursor.getString(1);

			// Filter
			if (!treatedNumbers.contains(phoneNumber)){
				matrix.addRow(new Object[]{cursor.getLong(0), 
						phoneNumber,
						cursor.getString(2),
						cursor.getInt(3),
						cursor.getLong(4)});
				treatedNumbers.add(phoneNumber);
			}
		}
		cursor.close();
		
		return new ContactListAdapter(activity, matrix);
	}
	
	/**
	 * Create a contact selector with RCS capable contacts
	 * 
	 * @param activity Activity
	 * @return List adapter
	 */
	public static ContactListAdapter createRcsContactListAdapter(Activity activity) {
	    String[] PROJECTION = new String[] {
	    		Phone._ID,
	    		Phone.NUMBER,
	    		Phone.LABEL,
	    		Phone.TYPE,
	    		Phone.CONTACT_ID
		    };
		MatrixCursor matrix = new MatrixCursor(PROJECTION);
	    
	    // Get the list of RCS contacts 
	    // TODO List<String> rcsContacts = contactsApi.getRcsContacts();
	    ContentResolver content = activity.getContentResolver();
	    
		// Query all phone numbers
        Cursor cursor = content.query(Phone.CONTENT_URI, 
        		PROJECTION, 
        		null, 
        		null, 
        		null);

		// List of unique number
		Vector<String> treatedNumbers = new Vector<String>();
		while (cursor.moveToNext()){
			// Keep a trace of already treated row
			String phoneNumber = cursor.getString(1);

			// If this number is RCS and not already in the list, take it 
// TODO			if (rcsContacts.contains(phoneNumber) && !treatedNumbers.contains(phoneNumber)){
				matrix.addRow(new Object[]{cursor.getLong(0), 
						phoneNumber,
						cursor.getString(2),
						cursor.getInt(3),
						cursor.getLong(4)});
				treatedNumbers.add(phoneNumber);
//			}
		}
		cursor.close();
		
		return new ContactListAdapter(activity, matrix);
	}

	/**
	 * Create a multi contacts selector with RCS capable contacts
	 * 
	 * @param activity Activity
	 * @return List adapter
	 */
	public static MultiContactListAdapter createMultiContactImCapableListAdapter(Activity activity) {
	    String[] PROJECTION = new String[] {
	    		Phone._ID,
	    		Phone.NUMBER,
	    		Phone.LABEL,
	    		Phone.TYPE,
	    		Phone.CONTACT_ID
		    };

		MatrixCursor matrix = new MatrixCursor(PROJECTION);

	    // Get the list of RCS contacts 
	    // List<String> rcsContacts = contactsApi.getRcsContacts();
	    ContentResolver content = activity.getContentResolver();

	    // Query all phone numbers
        Cursor cursor = content.query(Phone.CONTENT_URI, PROJECTION, null, null, null);

		// List of unique number
		Vector<String> treatedNumbers = new Vector<String>();
		while (cursor.moveToNext()){
			// Keep a trace of already treated row
			String phoneNumber = cursor.getString(1);
			
			// If this number is RCS and not already in the list, take it 
// TODO			if (rcsContacts.contains(phoneNumber) && !treatedNumbers.contains(phoneNumber)){
				matrix.addRow(new Object[]{cursor.getLong(0), 
						phoneNumber,
						cursor.getString(2),
						cursor.getInt(3),
						cursor.getLong(4)});
				treatedNumbers.add(phoneNumber);
//			}
		}
		cursor.close();
		return new MultiContactListAdapter(activity, matrix);
	}
	
	/**
	 * Display a toast
	 * 
	 * @param activity Activity
	 * @param message Message to be displayed
	 */
    public static void displayToast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

	/**
	 * Display a long toast
	 * 
	 * @param activity Activity
	 * @param message Message to be displayed
	 */
    public static void displayLongToast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
    
    /**
	 * Show a message and exit activity
	 * 
	 * @param activity Activity
	 * @param msg Message to be displayed
	 */
    public static void showMessageAndExit(final Activity activity, String msg) {
        if (activity.isFinishing()) {
        	return;
        }

		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(msg);
		builder.setTitle(R.string.title_msg);
		builder.setCancelable(false);
		builder.setPositiveButton(activity.getString(R.string.label_ok),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						activity.finish();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
    }

	/**
	 * Show an message
	 * 
	 * @param activity Activity
	 * @param msg Message to be displayed
	 * @return Dialog
	 */
    public static AlertDialog showMessage(Activity activity, String msg) {
    	AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    	builder.setMessage(msg);
    	builder.setTitle(R.string.title_msg);
    	builder.setCancelable(false);
    	builder.setPositiveButton(activity.getString(R.string.label_ok), null);
    	AlertDialog alert = builder.create();
    	alert.show();
		return alert;
    }

	/**
	 * Show a message with a specific title
	 * 
	 * @param activity Activity
	 * @param title Title of the dialog
	 * @param msg Message to be displayed
	 * @return Dialog
	 */
    public static AlertDialog showMessage(Activity activity, String title, String msg) {
    	AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    	builder.setMessage(msg);
    	builder.setTitle(title);
    	builder.setCancelable(false);
    	builder.setPositiveButton(activity.getString(R.string.label_ok), null);
    	AlertDialog alert = builder.create();
    	alert.show();
		return alert;
    }

    /**
	 * Show a picture and exit activity
	 * 
	 * @param activity Activity
	 * @param url Picture to be displayed
	 */
    public static void showPictureAndExit(final Activity activity, String url) {
        if (activity.isFinishing()) {
        	return;
        }
        
        Toast.makeText(activity, activity.getString(R.string.label_receive_image, url), Toast.LENGTH_LONG).show();

        Intent intent = new Intent();  
        intent.setAction(android.content.Intent.ACTION_VIEW);  
        File file = new File(url);  
        intent.setDataAndType(Uri.fromFile(file), "image/*");  
        activity.startActivity(intent);        
    }
  
    /**
	 * Show an info with a specific title
	 * 
	 * @param activity Activity
	 * @param title Title of the dialog
	 * @param items List of items
	 */
    public static void showList(Activity activity, String title, Set<String> items) {
        if (activity.isFinishing()) {
        	return;
        }
        
        CharSequence[] chars = items.toArray(new CharSequence[items.size()]);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    	builder.setTitle(title);
    	builder.setCancelable(false);
    	builder.setPositiveButton(activity.getString(R.string.label_ok), null);
        builder.setItems(chars, null);
        AlertDialog alert = builder.create();
    	alert.show();
    }
    
	/**
	 * Show a progress dialog with the given parameters 
	 * 
	 * @param activity Activity
	 * @param msg Message to be displayed
	 * @return Dialog
	 */
	public static ProgressDialog showProgressDialog(Activity activity, String msg) {
        ProgressDialog dlg = new ProgressDialog(activity);
		dlg.setMessage(msg);
		dlg.setIndeterminate(true);
		dlg.setCancelable(true);
		dlg.setCanceledOnTouchOutside(false);
		dlg.show();
		return dlg;
	}    

	/**
     * Format a date to string
     * 
     * @param d Date
     * @return String
     */
    public static String formatDateToString(long d) {
    	if (d > 0L) {
	    	Date df = new Date(d);
	    	return df.toLocaleString();
    	} else {
    		return "";
    	}
    }
}