package com.orangelabs.rcs.service.api;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.gsma.joyn.chat.ChatIntent;
import org.gsma.joyn.chat.ChatMessage;
import org.gsma.joyn.chat.GroupChatIntent;
import org.gsma.joyn.chat.IChat;
import org.gsma.joyn.chat.IChatListener;
import org.gsma.joyn.chat.IChatService;
import org.gsma.joyn.chat.IGroupChat;
import org.gsma.joyn.chat.IGroupChatListener;
import org.gsma.joyn.chat.INewChatListener;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;

import com.orangelabs.rcs.core.Core;
import com.orangelabs.rcs.core.ims.service.im.chat.ChatSession;
import com.orangelabs.rcs.core.ims.service.im.chat.GroupChatSession;
import com.orangelabs.rcs.core.ims.service.im.chat.OneOneChatSession;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.provider.messaging.RichMessaging;
import com.orangelabs.rcs.service.api.client.messaging.InstantMessage;
import com.orangelabs.rcs.service.api.server.ServerApiException;
import com.orangelabs.rcs.service.api.server.ServerApiUtils;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Chat service implementation
 * 
 * @author Jean-Marc AUFFRET
 */
public class ChatServiceImpl extends IChatService.Stub {
	/**
	 * List of chat sessions
	 */
	private static Hashtable<String, IChat> chatSessions = new Hashtable<String, IChat>();  

	/**
	 * List of group chat sessions
	 */
	private static Hashtable<String, IGroupChat> groupChatSessions = new Hashtable<String, IGroupChat>();  

	/**
	 * List of file chat invitation listeners
	 */
	private RemoteCallbackList<INewChatListener> listeners = new RemoteCallbackList<INewChatListener>();

	/**
	 * The logger
	 */
	private static Logger logger = Logger.getLogger(ChatServiceImpl.class.getName());

	/**
	 * Lock used for synchronization
	 */
	private Object lock = new Object();

	/**
	 * Constructor
	 */
	public ChatServiceImpl() {
		if (logger.isActivated()) {
			logger.info("Chat service API is loaded");
		}
	}

	/**
	 * Close API
	 */
	public void close() {
		// Clear lists of sessions
		chatSessions.clear();
		groupChatSessions.clear();
	}
	
	/**
	 * Receive a new chat invitation
	 * 
	 * @param session Chat session
	 */
    public void receiveOneOneChatInvitation(OneOneChatSession session) {
		if (logger.isActivated()) {
			logger.info("Receive chat invitation from " + session.getRemoteContact());
		}

		// Extract number from contact 
		String number = PhoneUtils.extractNumberFromUri(session.getRemoteContact());

		// Update rich messaging history
		RichMessaging.getInstance().addIncomingChatSession(session);

		// Add session in the list
		ChatImpl sessionApi = new ChatImpl(session);
		ChatServiceImpl.addChatSession(sessionApi);

		// Broadcast intent related to the received invitation
    	Intent intent = new Intent(ChatIntent.ACTION_NEW_INVITATION);
    	intent.putExtra(ChatIntent.EXTRA_CONTACT, number);
    	intent.putExtra(ChatIntent.EXTRA_DISPLAY_NAME, session.getRemoteDisplayName());
    	intent.putExtra(ChatIntent.EXTRA_CHAT_ID, session.getContributionID()); // TODO: which id ?
    	InstantMessage msg = session.getFirstMessage();
    	ChatMessage msgApi = new ChatMessage(msg.getMessageId(),
    			msg.getRemote(), msg.getTextMessage(), msg.getServerDate(),
    			msg.isImdnDisplayedRequested());
    	intent.putExtra(ChatIntent.EXTRA_FIRST_MESSAGE, msgApi);
    	AndroidFactory.getApplicationContext().sendBroadcast(intent);
    }
    
	/**
	 * Extend a 1-1 chat session
	 * 
     * @param groupSession Group chat session
     * @param oneoneSession 1-1 chat session
	 */
    public void extendOneOneChatSession(GroupChatSession groupSession, OneOneChatSession oneoneSession) {
		if (logger.isActivated()) {
			logger.info("Extend a 1-1 chat session");
		}

		// Add session in the list
		GroupChatImpl sessionApi = new GroupChatImpl(groupSession);
		ChatServiceImpl.addGroupChatSession(sessionApi);
    }

    /**
     * Receive message delivery status
     * 
	 * @param contact Contact
	 * @param msgId Message ID
     * @param status Delivery status
     */
    public void receiveMessageDeliveryStatus(String contact, String msgId, String status) {
    	synchronized(lock) {
			if (logger.isActivated()) {
				logger.info("Receive message delivery status for message " + msgId + ", status " + status);
			}
	
			// Update rich messaging history
			RichMessaging.getInstance().setChatMessageDeliveryStatus(msgId, status);
			
	  		// Notify message delivery listeners
/*			final int N = listeners.beginBroadcast();
	        for (int i=0; i < N; i++) {
	            try {
	            	listeners.getBroadcastItem(i).receiveMessageDeliveryStatus(contact, msgId, status);
	            } catch(Exception e) {
	            	if (logger.isActivated()) {
	            		logger.error("Can't notify listener", e);
	            	}
	            }
	        }
	        listeners.finishBroadcast();*/ // TODO
    	}
    }

    /**
	 * Receive a new group chat invitation
	 * 
	 * @param session Chat session
	 */
    public void receiveGroupChatInvitation(GroupChatSession session) {
		if (logger.isActivated()) {
			logger.info("Receive chat invitation from " + session.getRemoteContact());
		}

		// Extract number from contact 
		String number = PhoneUtils.extractNumberFromUri(session.getRemoteContact());

		// Update rich messaging history
		RichMessaging.getInstance().addIncomingChatSession(session);

		// Add session in the list
		GroupChatImpl sessionApi = new GroupChatImpl(session);
		ChatServiceImpl.addGroupChatSession(sessionApi);

		// Broadcast intent related to the received invitation
    	Intent intent = new Intent(GroupChatIntent.ACTION_NEW_INVITATION);
    	intent.putExtra(GroupChatIntent.EXTRA_CONTACT, number);
    	intent.putExtra(GroupChatIntent.EXTRA_DISPLAY_NAME, session.getRemoteDisplayName());
    	intent.putExtra(GroupChatIntent.EXTRA_CHAT_ID, session.getContributionID()); // TODO: which id ?
    	intent.putExtra(GroupChatIntent.EXTRA_SUBJECT, session.getSubject());
    	AndroidFactory.getApplicationContext().sendBroadcast(intent);
    }

	/**
	 * Add a chat session in the list
	 * 
	 * @param session Chat session
	 */
	protected static void addChatSession(ChatImpl session) {
		if (logger.isActivated()) {
			logger.debug("Add a chat session in the list (size=" + chatSessions.size() + ")");
		}
		chatSessions.put(session.getChatId(), session);
	}

	/**
	 * Remove a chat session from the list
	 * 
	 * @param sessionId Session ID
	 */
	protected static void removeChatSession(String sessionId) {
		if (logger.isActivated()) {
			logger.debug("Remove a chat session from the list (size=" + chatSessions.size() + ")");
		}
		chatSessions.remove(sessionId);
	}
    
	/**
	 * Add a group chat session in the list
	 * 
	 * @param session Chat session
	 */
	protected static void addGroupChatSession(GroupChatImpl session) {
		if (logger.isActivated()) {
			logger.debug("Add a chat session in the list (size=" + chatSessions.size() + ")");
		}
		groupChatSessions.put(session.getChatId(), session);
	}

	/**
	 * Remove a group chat session from the list
	 * 
	 * @param sessionId Session ID
	 */
	protected static void removeGroupChatSession(String sessionId) {
		if (logger.isActivated()) {
			logger.debug("Remove a chat session from the list (size=" + chatSessions.size() + ")");
		}
		groupChatSessions.remove(sessionId);
	}

	/**
     * Creates a single chat with a given contact and returns a Chat instance.
     * The parameter contact supports the following formats: MSISDN in national
     * or international format, SIP address, SIP-URI or Tel-URI.
     * 
     * @param contact Contact
     * @param firstMessage First message
     * @param listener Chat event listener
     * @return Chat
	 * @throws ServerApiException
     */
    public IChat initiateSingleChat(String contact, String firstMessage, IChatListener listener) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Initiate a 1-1 chat session with " + contact);
		}

    	// Check permission
		ServerApiUtils.testPermission();

		// Test IMS connection
		ServerApiUtils.testIms();
		
		try {
			// Initiate the session
			ChatSession session = Core.getInstance().getImService().initiateOne2OneChatSession(contact, firstMessage);
			
			// Update rich messaging history
			RichMessaging.getInstance().addOutgoingChatSession(session);
			
			// Add session in the list
			ChatImpl sessionApi = new ChatImpl((OneOneChatSession)session);
			ChatServiceImpl.addChatSession(sessionApi);
			return sessionApi;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
    }
    
    /**
     * Initiates a group chat with a group of contact and returns a GroupChat
     * instance. The subject is optional and may be null.
     * 
     * @param contact List of contacts
     * @param subject Subject
     * @param listener Chat event listener
	 * @throws ServerApiException
     */
    public IGroupChat initiateGroupChat(List<String> contacts, String subject, IGroupChatListener listener) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Initiate an ad-hoc group chat session");
		}
		
    	// Check permission
		ServerApiUtils.testPermission();

		// Test IMS connection
		ServerApiUtils.testIms();

		try {
			// Initiate the session
			ChatSession session = Core.getInstance().getImService().initiateAdhocGroupChatSession(contacts, subject);

			// Update rich messaging history
			RichMessaging.getInstance().addOutgoingChatSession(session);
			
			// Add session in the list
			GroupChatImpl sessionApi = new GroupChatImpl((GroupChatSession)session);
			ChatServiceImpl.addGroupChatSession(sessionApi);
			return sessionApi;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
    }
    
    /**
     * Rejoins an existing group chat from its unique chat ID
     * 
     * @param chatId Chat ID
     * @return Group chat
     * @throws ServerApiException
     */
    public IGroupChat rejoinGroupChat(String chatId) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Rejoin group chat session related to the conversation " + chatId);
		}
		
    	// Check permission
		ServerApiUtils.testPermission();

		// Test IMS connection
		ServerApiUtils.testIms();

		try {
			// Initiate the session
			ChatSession session = Core.getInstance().getImService().rejoinGroupChatSession(chatId);

			// Add session in the list
			GroupChatImpl sessionApi = new GroupChatImpl((GroupChatSession)session);
			ChatServiceImpl.addGroupChatSession(sessionApi);
			return sessionApi;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
    }
    
    /**
     * Restarts a previous group chat from its unique chat ID
     * 
     * @param chatId Chat ID
     * @return Group chat
     * @throws ServerApiException
     */
    public IGroupChat restartGroupChat(String chatId) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Restart group chat session related to the conversation " + chatId);
		}
		
    	// Check permission
		ServerApiUtils.testPermission();

		// Test IMS connection
		ServerApiUtils.testIms();

		try {
			// Initiate the session
			ChatSession session = Core.getInstance().getImService().restartGroupChatSession(chatId);

			// Add session in the list
			GroupChatImpl sessionApi = new GroupChatImpl((GroupChatSession)session);
			ChatServiceImpl.addGroupChatSession(sessionApi);
			return sessionApi;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
    }
    
    /**
     * Deletes a particular chat conversation (single/group)
     * 
     * @param chatId Chat ID
     * @throws ServerApiException
     */
    public void deleteChat(String chatId) throws ServerApiException {
    	// TODO
    }
    
    /**
     * Adds a listener on new chat invitation event
     * 
     * @param listener Chat invitation listener
     * @throws ServerApiException
     */
    public void addEventListener(INewChatListener listener) throws ServerApiException {
    	// TODO
    }
    
    /**
     * Removes a listener on new chat invitation event.
     * 
     * @param listener Chat invitation listener
     * @throws ServerApiException
     */
    public void removeEventListener(INewChatListener listener) throws ServerApiException {
    	// TODO
    }
    
    /**
     * Returns the list of single chats in progress
     * 
     * @return List of chats
     * @throws ServerApiException
     */
    public List<IBinder> getChats() throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get chat sessions");
		}

		// Check permission
		ServerApiUtils.testPermission();

		// Test core availability
		ServerApiUtils.testCore();
		
		try {
			ArrayList<IBinder> result = new ArrayList<IBinder>(chatSessions.size());
			for (Enumeration<IChat> e = chatSessions.elements() ; e.hasMoreElements() ;) {
				IChat sessionApi = (IChat)e.nextElement() ;
				result.add(sessionApi.asBinder());
			}
			return result;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
    }
    
    /**
     * Returns a chat in progress from its unique ID
     * 
     * @param chatId Chat ID
     * @return Chat or null if not found
     * @throws ServerApiException
     */
    public IChat getChat(String chatId) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get chat session " + chatId);
		}

		// Check permission
		ServerApiUtils.testPermission();

		// Test core availability
		ServerApiUtils.testCore();

		// Return a session instance
		return chatSessions.get(chatId);
    }
    
    /**
     * Returns the list of group chats in progress
     * 
     * @return List of group chat
     * @throws ServerApiException
     */
    public List<IBinder> getGroupChats() throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get group chat sessions");
		}

		// Check permission
		ServerApiUtils.testPermission();

		// Test core availability
		ServerApiUtils.testCore();
		
		try {
			ArrayList<IBinder> result = new ArrayList<IBinder>(chatSessions.size());
			for (Enumeration<IGroupChat> e = groupChatSessions.elements() ; e.hasMoreElements() ;) {
				IGroupChat sessionApi = (IGroupChat)e.nextElement() ;
				result.add(sessionApi.asBinder());
			}
			return result;
		} catch(Exception e) {
			if (logger.isActivated()) {
				logger.error("Unexpected error", e);
			}
			throw new ServerApiException(e.getMessage());
		}
    }
    
    /**
     * Returns a group chat in progress from its unique ID
     * 
     * @param chatId Chat ID
     * @return Group chat or null if not found
     * @throws ServerApiException
     */
    public IGroupChat getGroupChat(String chatId) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Get group chat session " + chatId);
		}

		// Check permission
		ServerApiUtils.testPermission();

		// Test core availability
		ServerApiUtils.testCore();

		// Return a session instance
		return groupChatSessions.get(chatId);
	}

    /**
	 * Registers a chat invitation listener
	 * 
	 * @param listener New file transfer listener
	 * @throws ServerApiException
	 */
	public void addNewFileTransferListener(INewChatListener listener) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Add a chat invitation listener");
		}

		listeners.register(listener);
	}

	/**
	 * Unregisters a chat invitation listener
	 * 
	 * @param listener New file transfer listener
	 * @throws ServerApiException
	 */
	public void removeNewFileTransferListener(INewChatListener listener) throws ServerApiException {
		if (logger.isActivated()) {
			logger.info("Remove a chat invitation listener");
		}

		listeners.unregister(listener);
	}
}
