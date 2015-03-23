/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
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
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.core.ims.service.im.filetransfer.http;

import com.gsma.rcs.core.content.MmContent;
import com.gsma.rcs.core.ims.protocol.sip.SipException;
import com.gsma.rcs.core.ims.protocol.sip.SipRequest;
import com.gsma.rcs.core.ims.service.ImsService;
import com.gsma.rcs.core.ims.service.ImsServiceError;
import com.gsma.rcs.core.ims.service.ImsSessionListener;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileSharingError;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileSharingSession;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileSharingSessionListener;
import com.gsma.rcs.provider.fthttp.FtHttpResume;
import com.gsma.rcs.provider.messaging.MessagingLog;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.contact.ContactId;
import com.gsma.services.rcs.filetransfer.FileTransfer;

import java.util.Collection;

/**
 * Abstract file transfer HTTP session
 * 
 * @author jexa7410
 */
public abstract class HttpFileTransferSession extends FileSharingSession {

    /**
     * Chat session ID
     */
    private String mChatSessionId;

    /**
     * Session state
     */
    private int mSessionState;

    /**
     * Data object to access the resume FT instance in DB
     */
    protected FtHttpResume mResumeFT;

    protected final MessagingLog mMessagingLog;

    /**
     * The logger
     */
    private static final Logger sLogger = Logger.getLogger(HttpFileTransferSession.class
            .getSimpleName());

    /**
     * Constructor
     * 
     * @param parent IMS service
     * @param content Content to share
     * @param contact Remote contact identifier
     * @param remoteUri the remote URI
     * @param fileIcon Content of file icon
     * @param chatSessionId Chat session ID
     * @param chatContributionId Chat contribution Id
     * @param fileTransferId File transfer Id
     * @param rcsSettings
     * @param messagingLog
     * @param timestamp Local timestamp for the session
     */
    public HttpFileTransferSession(ImsService parent, MmContent content, ContactId contact,
            String remoteUri, MmContent fileIcon, String chatSessionId, String chatContributionId,
            String fileTransferId, RcsSettings rcsSettings, MessagingLog messagingLog,
            long timestamp) {
        super(parent, content, contact, remoteUri, fileIcon, fileTransferId, rcsSettings, timestamp);
        mChatSessionId = chatSessionId;
        setContributionID(chatContributionId);
        mSessionState = HttpTransferState.PENDING;
        mMessagingLog = messagingLog;
    }

    @Override
    public boolean isHttpTransfer() {
        return true;
    }

    /**
     * Returns the chat session ID associated to the transfer
     * 
     * @return the chatSessionID
     */
    public String getChatSessionID() {
        return mChatSessionId;
    }

    /**
     * Set the chatSessionId
     * 
     * @param chatSessionID
     */
    public void setChatSessionID(String chatSessionID) {
        this.mChatSessionId = chatSessionID;
    }

    /**
     * Create an INVITE request
     * 
     * @return the INVITE request
     * @throws SipException
     */
    public SipRequest createInvite() throws SipException {
        // Not used here
        return null;
    }

    private void closeSession(TerminationReason reason) {
        interruptSession();

        terminateSession(reason);

        removeSession();
    }

    @Override
    public void abortSession(TerminationReason reason) {
        /* If reason is TERMINATION_BY_SYSTEM and session already started, then it's a pause */
        String fileTransferId = getFileTransferId();
        FileTransfer.State state = mMessagingLog.getFileTransferState(fileTransferId);
        if (TerminationReason.TERMINATION_BY_SYSTEM == reason) {
            switch (state) {
                case INVITED:
                    closeSession(reason);
                    return;
                case STARTED:
                    if (!isInitiatedByRemote()
                            && mMessagingLog.getFileTransferUploadTid(fileTransferId) == null) {
                        break;
                    }
                    closeSession(reason);
                    if (sLogger.isActivated()) {
                        sLogger.info("Pause the session (session terminated, but can be resumed)");
                    }

                    ContactId contact = getRemoteContact();
                    for (ImsSessionListener listener : getListeners()) {
                        ((FileSharingSessionListener) listener)
                                .handleFileTransferPausedBySystem(contact);
                    }
                    return;
                case PAUSED:
                    closeSession(reason);
                    return;
                default:
                    break;
            }
        }
        super.abortSession(reason);
    }

    /**
     * Handle error
     * 
     * @param error Error
     */
    public void handleError(ImsServiceError error) {
        if (isSessionInterrupted()) {
            return;
        }

        // Error
        if (sLogger.isActivated()) {
            sLogger.info("Transfer error: " + error.getErrorCode() + ", reason="
                    + error.getMessage());
        }

        // Remove the current session
        removeSession();

        Collection<ImsSessionListener> listeners = getListeners();
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : listeners) {
            ((FileSharingSessionListener) listener).handleTransferError(
                    new FileSharingError(error), contact);
        }
    }

    /**
     * Prepare media session
     * 
     * @throws Exception
     */
    public void prepareMediaSession() throws Exception {
        // Not used here
    }

    /**
     * Start media session
     * 
     * @throws Exception
     */
    public void startMediaSession() throws Exception {
        // Not used here
    }

    /**
     * Close media session
     */
    public void closeMediaSession() {
        // Not used here
    }

    /**
     * Handle file transfered. In case of file transfer over MSRP, the terminating side has received
     * the file, but in case of file transfer over HTTP, only the content server has received the
     * file.
     */
    public void handleFileTransfered() {
        // File has been transfered
        fileTransfered();

        // Remove the current session
        removeSession();

        Collection<ImsSessionListener> listeners = getListeners();
        ContactId contact = getRemoteContact();
        MmContent content = getContent();
        for (ImsSessionListener listener : listeners) {
            ((FileSharingSessionListener) listener).handleFileTransfered(content, contact);
        }
    }

    /**
     * HTTP transfer progress HttpTransferEventListener implementation
     * 
     * @param currentSize Current transfered size in bytes
     * @param totalSize Total size in bytes
     */
    public void httpTransferProgress(long currentSize, long totalSize) {
        Collection<ImsSessionListener> listeners = getListeners();
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : listeners) {
            ((FileSharingSessionListener) listener).handleTransferProgress(contact, currentSize,
                    totalSize);
        }
    }

    /**
     * HTTP not allowed to send
     */
    public void httpTransferNotAllowedToSend() {
        Collection<ImsSessionListener> listeners = getListeners();
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : listeners) {
            ((FileSharingSessionListener) listener).handleTransferNotAllowedToSend(contact);
        }
    }

    /**
     * HTTP transfer started HttpTransferEventListener implementation
     */
    public void httpTransferStarted() {
        this.mSessionState = HttpTransferState.ESTABLISHED;
        ContactId contact = getRemoteContact();
        for (int j = 0; j < getListeners().size(); j++) {
            ((FileSharingSessionListener) getListeners().get(j)).handleSessionStarted(contact);
        }
    }

    /**
     * Handle file transfer paused by user
     */
    public void httpTransferPausedByUser() {
        Collection<ImsSessionListener> listeners = getListeners();
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : listeners) {
            ((FileSharingSessionListener) listener).handleFileTransferPausedByUser(contact);
        }
    }

    /**
     * Handle file transfer paused by system
     */
    public void httpTransferPausedBySystem() {
        Collection<ImsSessionListener> listeners = getListeners();
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : listeners) {
            ((FileSharingSessionListener) listener).handleFileTransferPausedBySystem(contact);
        }
    }

    /**
     * Handle file transfer paused
     */
    public void httpTransferResumed() {
        Collection<ImsSessionListener> listeners = getListeners();
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : listeners) {
            ((FileSharingSessionListener) listener).handleFileTransferResumed(contact);
        }
    }

    /**
     * Get session state
     * 
     * @return State
     */
    public int getSessionState() {
        return mSessionState;
    }

    /**
     * Pausing file transfer Implementation should be overridden in subclasses
     */
    public void pauseFileTransfer() {
        if (sLogger.isActivated()) {
            sLogger.debug("Pausing is not available");
        }
    }

    /**
     * Resuming file transfer Implementation should be overridden in subclasses
     */
    public void resumeFileTransfer() {
        if (sLogger.isActivated()) {
            sLogger.debug("Resuming is not available");
        }
    }
}