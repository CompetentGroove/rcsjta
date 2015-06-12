/*
 * Copyright (C) 2015 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.gsma.rcs.provider.history;

import com.gsma.rcs.core.Core;
import com.gsma.rcs.core.content.MmContent;
import com.gsma.rcs.core.ims.protocol.msrp.MsrpException;
import com.gsma.rcs.core.ims.service.im.chat.ChatMessage;
import com.gsma.rcs.core.ims.service.im.chat.ChatUtils;
import com.gsma.rcs.core.ims.service.im.chat.imdn.ImdnManager;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileTransferUtils;
import com.gsma.rcs.provider.contact.ContactManager;
import com.gsma.rcs.provider.messaging.FileTransferData;
import com.gsma.rcs.provider.messaging.MessageData;
import com.gsma.rcs.provider.messaging.MessagingLog;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.service.DequeueTask;
import com.gsma.rcs.service.api.ChatServiceImpl;
import com.gsma.rcs.service.api.FileTransferServiceImpl;
import com.gsma.rcs.service.api.GroupChatImpl;
import com.gsma.rcs.service.api.GroupFileTransferImpl;
import com.gsma.services.rcs.filetransfer.FileTransfer.ReasonCode;
import com.gsma.services.rcs.filetransfer.FileTransfer.State;

import android.database.Cursor;
import android.net.Uri;

/**
 * GroupChatDequeueTask tries to dequeue all group chat messages that are QUEUED and all file
 * transfers that are either QUEUED or UPLOADED but not trasnferred for a specific group chat.
 */
public class GroupChatDequeueTask extends DequeueTask {

    private final String mChatId;

    private final ChatServiceImpl mChatService;

    private final FileTransferServiceImpl mFileTransferService;

    private final HistoryLog mHistoryLog;

    private final boolean mDisplayedReportEnabled;

    private final boolean mDeliveryReportEnabled;

    public GroupChatDequeueTask(Object lock, Core core, String chatId, MessagingLog messagingLog,
            ChatServiceImpl chatService, FileTransferServiceImpl fileTransferService,
            RcsSettings rcsSettings, HistoryLog historyLog, ContactManager contactManager) {
        super(lock, core, contactManager, messagingLog, rcsSettings);
        mChatId = chatId;
        mChatService = chatService;
        mFileTransferService = fileTransferService;
        mHistoryLog = historyLog;
        final ImdnManager imdnManager = mImService.getImdnManager();
        mDisplayedReportEnabled = imdnManager.isRequestGroupDeliveryDisplayedReportsEnabled();
        mDeliveryReportEnabled = imdnManager.isDeliveryDeliveredReportsEnabled();
    }

    public void run() {
        boolean logActivated = mLogger.isActivated();
        if (logActivated) {
            mLogger.debug("Execute task to dequeue group chat messages and group file transfers for chatId "
                    .concat(mChatId));
        }
        Cursor cursor = null;
        try {
            synchronized (mLock) {
                if (mCore.isStopping()) {
                    if (logActivated) {
                        mLogger.debug("Core service is stopped, exiting dequeue task to dequeue group chat messages and group file transfers for chatId "
                                .concat(mChatId));
                    }
                    return;
                }
                cursor = mHistoryLog.getQueuedGroupChatMessagesAndGroupFileTransfers(mChatId);
                /* TODO: Handle cursor when null. */
                int providerIdIdx = cursor.getColumnIndexOrThrow(HistoryLogData.KEY_PROVIDER_ID);
                int idIdx = cursor.getColumnIndexOrThrow(HistoryLogData.KEY_ID);
                int contentIdx = cursor.getColumnIndexOrThrow(HistoryLogData.KEY_CONTENT);
                int mimeTypeIdx = cursor.getColumnIndexOrThrow(HistoryLogData.KEY_MIME_TYPE);
                int fileIconIdx = cursor.getColumnIndexOrThrow(HistoryLogData.KEY_FILEICON);
                int statusIdx = cursor.getColumnIndexOrThrow(HistoryLogData.KEY_STATUS);
                int fileSizeIdx = cursor.getColumnIndexOrThrow(HistoryLogData.KEY_FILESIZE);
                GroupChatImpl groupChat = mChatService.getOrCreateGroupChat(mChatId);
                while (cursor.moveToNext()) {
                    if (mCore.isStopping()) {
                        if (logActivated) {
                            mLogger.debug("Core service is stopped, exiting dequeue task to dequeue group chat messages and group file transfers for chatId "
                                    .concat(mChatId));
                        }
                        return;
                    }
                    int providerId = cursor.getInt(providerIdIdx);
                    String id = cursor.getString(idIdx);
                    try {
                        switch (providerId) {
                            case MessageData.HISTORYLOG_MEMBER_ID:
                                if (!isAllowedToDequeueGroupChatMessage(mChatId)) {
                                    continue;
                                }
                                try {
                                    String content = cursor.getString(contentIdx);
                                    String mimeType = cursor.getString(mimeTypeIdx);
                                    long timestamp = System.currentTimeMillis();
                                    /* For outgoing message, timestampSent = timestamp */
                                    ChatMessage message = ChatUtils.createChatMessage(id, mimeType,
                                            content, null, null, timestamp, timestamp);
                                    groupChat.dequeueGroupChatMessage(message);

                                } catch (MsrpException e) {
                                    if (logActivated) {
                                        mLogger.debug(new StringBuilder(
                                                "Failed to dequeue group chat message '")
                                                .append(id).append("' message on group chat '")
                                                .append(mChatId).append("' due to: ")
                                                .append(e.getMessage()).toString());
                                    }
                                }
                                break;
                            case FileTransferData.HISTORYLOG_MEMBER_ID:
                                if (mImService.isFileSizeExceeded(cursor.getLong(fileSizeIdx))) {
                                    mFileTransferService.setGroupFileTransferStateAndReasonCode(id,
                                            mChatId, State.FAILED,
                                            ReasonCode.FAILED_NOT_ALLOWED_TO_SEND);
                                    continue;
                                }
                                State state = State.valueOf(cursor.getInt(statusIdx));
                                switch (state) {
                                    case QUEUED:
                                        if (!isAllowedToDequeueGroupFileTransfer(mChatId)) {
                                            continue;
                                        }
                                        try {
                                            Uri file = Uri.parse(cursor.getString(contentIdx));
                                            MmContent fileContent = FileTransferUtils
                                                    .createMmContent(file);
                                            MmContent fileIconContent = null;
                                            String fileIcon = cursor.getString(fileIconIdx);
                                            if (fileIcon != null) {
                                                Uri fileIconUri = Uri.parse(fileIcon);
                                                fileIconContent = FileTransferUtils
                                                        .createMmContent(fileIconUri);
                                            }
                                            mFileTransferService.dequeueGroupFileTransfer(mChatId,
                                                    id, fileContent, fileIconContent);
                                        } catch (MsrpException e) {
                                            if (logActivated) {
                                                mLogger.debug(new StringBuilder(
                                                        "Failed to dequeue group file transfer with fileTransferId '")
                                                        .append(id).append("' on group chat '")
                                                        .append(mChatId).append("' due to: ")
                                                        .append(e.getMessage()).toString());
                                            }
                                        } catch (SecurityException e) {
                                            mLogger.error(
                                                    new StringBuilder(
                                                            "Security exception occured while dequeueing file transfer with transferId '")
                                                            .append(id)
                                                            .append("', so mark as failed")
                                                            .toString(), e);
                                            mFileTransferService
                                                    .setGroupFileTransferStateAndReasonCode(id,
                                                            mChatId, State.FAILED,
                                                            ReasonCode.FAILED_NOT_ALLOWED_TO_SEND);
                                        }
                                        break;
                                    case STARTED:
                                        if (!isAllowedToDequeueGroupChatMessage(mChatId)) {
                                            continue;
                                        }
                                        try {
                                            GroupFileTransferImpl groupFileTransfer = mFileTransferService
                                                    .getOrCreateGroupFileTransfer(mChatId, id);
                                            String fileInfo = FileTransferUtils
                                                    .createHttpFileTransferXml(mMessagingLog
                                                            .getGroupFileDownloadInfo(id));
                                            groupChat.dequeueGroupFileInfo(id, fileInfo,
                                                    mDisplayedReportEnabled,
                                                    mDeliveryReportEnabled, groupFileTransfer);

                                        } catch (MsrpException e) {
                                            if (logActivated) {
                                                mLogger.debug(new StringBuilder(
                                                        "Failed to dequeue group file info '")
                                                        .append(id)
                                                        .append("' message on group chat '")
                                                        .append(mChatId).append("' due to: ")
                                                        .append(e.getMessage()).toString());
                                            }
                                        } catch (SecurityException e) {
                                            mLogger.error(
                                                    new StringBuilder(
                                                            "Security exception occured while dequeueing file info with transferId '")
                                                            .append(id)
                                                            .append("', so mark as failed")
                                                            .toString(), e);
                                            mFileTransferService
                                                    .setGroupFileTransferStateAndReasonCode(id,
                                                            mChatId, State.FAILED,
                                                            ReasonCode.FAILED_NOT_ALLOWED_TO_SEND);
                                        }
                                        break;
                                    default:
                                        break;
                                }
                                break;
                            default:
                                break;
                        }
                    } catch (RuntimeException e) {
                        /*
                         * Break only for terminal exception, in rest of the cases dequeue and try
                         * to send other messages.
                         */
                        mLogger.error(
                                new StringBuilder(
                                        "Exception occured while dequeueing group chat message and group file transfer with id '")
                                        .append(id).append("' and chatId '").append(mChatId)
                                        .append("'").toString(), e);
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
