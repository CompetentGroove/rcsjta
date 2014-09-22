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
package com.orangelabs.rcs.ri.sharing.image;

import java.util.Calendar;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.gsma.services.rcs.RcsCommon;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.ish.ImageSharingIntent;
import com.orangelabs.rcs.ri.R;
import com.orangelabs.rcs.ri.utils.LogUtils;
import com.orangelabs.rcs.ri.utils.RcsDisplayName;
import com.orangelabs.rcs.ri.utils.Utils;

/**
 * Image sharing intent service
 * 
 * @author YPLO6403
 * 
 */
public class ImageSharingIntentService extends IntentService {

	/**
	 * The log tag for this class
	 */
	private static final String LOGTAG = LogUtils.getTag(ImageSharingIntentService.class.getSimpleName());

	static final String BUNDLE_ISHDAO_ID = "ishdao";

	public ImageSharingIntentService(String name) {
		super(name);
	}

	public ImageSharingIntentService() {
		super("ImageSharingIntentService");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		// We want this service to stop running if forced stop
		// so return not sticky.
		return START_NOT_STICKY;
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent == null || intent.getAction() == null) {
			return;
		}
		// Check action from incoming intent
		if (!intent.getAction().equalsIgnoreCase(ImageSharingIntent.ACTION_NEW_INVITATION)) {
			if (LogUtils.isActive) {
				Log.e(LOGTAG, "Unknown action " + intent.getAction());
			}
			return;
		}
		// Gets data from the incoming Intent
		String sharingId = intent.getStringExtra(ImageSharingIntent.EXTRA_SHARING_ID);
		if (sharingId == null) {
			if (LogUtils.isActive) {
				Log.e(LOGTAG, "Cannot read sharing ID");
			}
			return;
		}
		try {
			// Get Image sharing from provider
			ImageSharingDAO ishDao = new ImageSharingDAO(this, sharingId);
			// Save ImageSharingDAO into intent
			Bundle bundle = new Bundle();
			bundle.putParcelable(BUNDLE_ISHDAO_ID, ishDao);
			intent.putExtras(bundle);
			if (LogUtils.isActive) {
				Log.d(LOGTAG, "ISH invitation " + ishDao);
			}
			// TODO check ISH state to know if rejected
			// TODO check validity of direction, etc ...
			// Display invitation notification
			addImageSharingInvitationNotification(this, intent, ishDao);
		} catch (Exception e) {
			if (LogUtils.isActive) {
				Log.e(LOGTAG, "Cannot read ISH data from provider", e);
			}
		}
	}

	/**
	 * Add image share notification
	 * 
	 * @param context
	 *            Context
	 * @param intent
	 *            Intent invitation
	 * @param ishDao
	 *            the image sharing data object
	 */
	private void addImageSharingInvitationNotification(Context context, Intent invitation, ImageSharingDAO ishDao) {
		if (ishDao.getContact() == null) {
			if (LogUtils.isActive) {
				Log.e(LOGTAG, "addImageSharingInvitationNotification failed: cannot parse contact");
			}
			return;
		}
		// Create notification
		Intent intent = new Intent(invitation);
		intent.setClass(context, ReceiveImageSharing.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		ContactId contact = ishDao.getContact();
		String displayName = RcsDisplayName.get(context, contact);
		displayName = RcsDisplayName.convert(context, RcsCommon.Direction.INCOMING, contact, displayName);
		String title = context.getString(R.string.title_recv_image_sharing, displayName);

		// Create notification
		NotificationCompat.Builder notif = new NotificationCompat.Builder(context);
		notif.setContentIntent(contentIntent);
		notif.setSmallIcon(R.drawable.ri_notif_csh_icon);
		notif.setWhen(Calendar.getInstance().getTimeInMillis());
		notif.setAutoCancel(true);
		notif.setOnlyAlertOnce(true);
		notif.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
		notif.setDefaults(Notification.DEFAULT_VIBRATE);
		notif.setContentTitle(title);
		notif.setContentText(getString(R.string.label_from_args, ishDao.getFilename()));
				
		// Send notification
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(ishDao.getSharingId(), Utils.NOTIF_ID_IMAGE_SHARE, notif.build());
	}
}
