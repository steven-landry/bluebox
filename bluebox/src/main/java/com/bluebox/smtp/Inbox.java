package com.bluebox.smtp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.Preferences;

import javax.mail.Address;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexNotFoundException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.helper.SimpleMessageListener;

import com.bluebox.Config;
import com.bluebox.Utils;
import com.bluebox.WorkerThread;
import com.bluebox.search.SearchIndexer;
import com.bluebox.search.SearchIndexer.SearchFields;
import com.bluebox.smtp.storage.BlueboxMessage;
import com.bluebox.smtp.storage.StorageFactory;

public class Inbox implements SimpleMessageListener {
	private static final String GLOBAL_COUNT_NODE = "global_message_count";
	Preferences prefs = Preferences.userNodeForPackage(Inbox.class);
	private JSONObject recentStats = new JSONObject();

	public static final String EMAIL = "Email";
	public static final String START = "Start";
	public static final String COUNT = "Count";
	public static final String ORDERBY = "OrderBy";
	private static final Logger log = LoggerFactory.getLogger(Inbox.class);
	private List<String> fromBlackList, toBlackList, toWhiteList, fromWhiteList;

	private static Timer timer = null;
	private static Inbox inbox;

	public static Inbox getInstance() {
		if (inbox == null) {
			inbox = new Inbox();
			inbox.start();
		}
		return inbox;
	}

	private Inbox() {
		StorageFactory.getInstance();
		loadConfig();
	}

	private void start() {
		log.info("Starting inbox");

		// ensure storage instance if loaded and started
		try {
			StorageFactory.getInstance().start();
		} 
		catch (Exception e) {
			log.error("Error starting storage instance",e.getMessage());
			e.printStackTrace();
		}
		// now start a background timer for the mail expiration
		long frequency = Config.getInstance().getLong(Config.BLUEBOX_DAEMON_DELAY);
		if (timer != null) {
			timer.cancel();
		}
		timer = new Timer();
		long period = frequency*60*1000;  // repeat every hour.
		long delay = period;   // delay for same amount of time before first run.
		timer = new Timer();

		timer.scheduleAtFixedRate(new TimerTask() {

			public void run() {
				log.info("Cleanup timer activated");
				try {
					cleanUp();
				} 
				catch (Exception e) {
					log.error("Error running message cleanup",e);
					e.printStackTrace();
				}
			}
		}, delay, period);
	}

	public void stop() {
		log.info("Stopping inbox");
		try {
			StorageFactory.getInstance().stop();
		}
		catch (Throwable e) {
			log.error("Error stopping storage :{}",e.getMessage());
		}
		log.info("Cleanup timer cancelled");
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		log.info("Stopping search engine");
		try {
			SearchIndexer.getInstance().stop();
		} 
		catch (IOException e) {
			e.printStackTrace();
			log.error("Error stopping search engine",e);
		}
		inbox = null;
	}

	public BlueboxMessage retrieve(String uid) throws Exception {
		return StorageFactory.getInstance().retrieve(uid);
	}

	/**
	 * Get the number of messages received.
	 * @return size of received email list
	 */
	public long getMailCount(BlueboxMessage.State state) {
		try {
			return StorageFactory.getInstance().getMailCount(state);
		} 
		catch (Exception e) {
			log.error(e.getMessage());
			e.printStackTrace();
		}
		return 0;
	}

	public long getMailCount(InboxAddress inbox, BlueboxMessage.State state) throws Exception {
		return StorageFactory.getInstance().getMailCount(inbox,state);
	}

	public void listInbox(InboxAddress inbox, BlueboxMessage.State state, Writer writer, int start, int count, String orderBy, boolean ascending, Locale locale) throws Exception {
		log.debug("Sending inbox contents for {}",inbox);
		StorageFactory.getInstance().listInbox(inbox, state, writer, start, count, orderBy, ascending, locale);
	}

	public List<BlueboxMessage> listInbox(InboxAddress inbox, BlueboxMessage.State state, int start, int count, String orderBy, boolean ascending) throws Exception {
		log.debug("Sending inbox contents for {}",inbox);
		return StorageFactory.getInstance().listMail(inbox, state, start, count, orderBy, ascending);
	}

	public List<JSONObject> listInboxLite(InboxAddress inbox, BlueboxMessage.State state, int start, int count, String orderBy, boolean ascending, Locale loc) throws Exception {
		log.debug("Sending inbox contents for {}",inbox);
		return StorageFactory.getInstance().listMailLite(inbox, state, start, count, orderBy, ascending, loc);
	}

	public long searchInbox(String search, Writer writer, int start, int count, SearchIndexer.SearchFields searchScope, SearchIndexer.SearchFields orderBy, boolean ascending) throws Exception {
		log.debug("Searching for {} ordered by {}",search,orderBy);
		try {
			return SearchIndexer.getInstance().searchInboxes(search, writer, start, count, searchScope, orderBy, ascending);
		}
		catch (IndexNotFoundException inf) {
			log.info("Detected index problems - rebuilding search indexes");
			// indexes messed up, so try auto-heal
			WorkerThread wt = rebuildSearchIndexes();
			new Thread(wt).start();
			return 0;
		}
	}

	public void delete(String uid) throws Exception {
		StorageFactory.getInstance().delete(uid);
	}

	public void deleteAll() {
		try {
			StorageFactory.getInstance().deleteAll();
			SearchIndexer.getInstance().deleteIndexes();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public WorkerThread cleanUp() throws Exception {
		WorkerThread wt = new WorkerThread("cleanup") {

			@Override
			public void run() {
				try {
					setProgress(30);
					// remove old messages
					expire();
					setProgress(60);
					// trim total mailbox size
					trim();
				}
				catch (Throwable t) {
					t.printStackTrace();
				}
				finally {
					setProgress(100);
				}
			}

		};

		return wt;
	}

	private void trim() {
		log.info("Trimming mailboxes");
		List<JSONObject> list;
		try {
			while (StorageFactory.getInstance().getMailCount(BlueboxMessage.State.NORMAL)>Config.getInstance().getLong(Config.BLUEBOX_MESSAGE_MAX)) {
				list = StorageFactory.getInstance().listMailLite(null, BlueboxMessage.State.NORMAL, 0, 50, BlueboxMessage.RECEIVED, true,Locale.getDefault());
				for (JSONObject msg : list) {
					delete(msg.getString(BlueboxMessage.UID));
				}
			}
		}
		catch (Throwable t) {
			log.error("Problem trimming mailboxes",t);
		}
	}

	private void expire() throws Exception {
		Date messageDate = new Date(new Date().getTime()-(Config.getInstance().getLong(Config.BLUEBOX_MESSAGE_AGE)*60*60*1000));
		Date trashDate = new Date(new Date().getTime()-(Config.getInstance().getLong(Config.BLUEBOX_TRASH_AGE)*60*60*1000));
		expire(messageDate, trashDate);
	}

	private void expire(Date messageExpireDate, Date trashExpireDate) throws Exception {
		List<BlueboxMessage> list;

		long count = 0;

		log.info("Cleaning messages received before "+messageExpireDate);
		list = StorageFactory.getInstance().listMail(null, BlueboxMessage.State.NORMAL, 0, -1, BlueboxMessage.RECEIVED, true);
		Date received;
		for (BlueboxMessage msg : list) {
			try {
				if ((received = msg.getReceived()).before(messageExpireDate)) {
					StorageFactory.getInstance().delete(msg.getIdentifier());
					SearchIndexer.getInstance().deleteDoc(msg.getIdentifier());
				}
				else {
					log.debug("Not deleting since received:"+received+" but expiry window:"+messageExpireDate);
				}
			}
			catch (Throwable t) {
				log.warn("Problem cleaning up message "+msg.getIdentifier()+" "+t.getMessage());
			}
		}
		log.info("Cleaned up "+count+" messages");

		log.info("Cleaning deleted messages received before "+trashExpireDate);
		count  = 0;
		list = StorageFactory.getInstance().listMail(null, BlueboxMessage.State.DELETED, 0, -1, BlueboxMessage.RECEIVED, true);
		for (BlueboxMessage msg : list) {
			try {
				if ((received = msg.getReceived()).before(trashExpireDate)) {
					StorageFactory.getInstance().delete(msg.getIdentifier());
					SearchIndexer.getInstance().deleteDoc(msg.getIdentifier());
					count++;
				}
				else {
					log.debug("Not deleting since received:"+received+" but expiry window:"+messageExpireDate);
				}				
			}
			catch (Throwable t) {
				log.warn("Problem cleaning up message "+msg.getIdentifier());
			}
		}
		log.info("Cleaned up "+count+" deleted messages");
	}

	@Override
	public boolean accept(String from, String recipient) {	

		try {
			InternetAddress fromIA = new InternetAddress(from);
			InternetAddress toIA = new InternetAddress(recipient);

			if (Config.getInstance().getBoolean(Config.BLUEBOX_STRICT_CHECKING)) {
				// validate the email format
				if (!from.contains("@"))return false;
				if (!recipient.contains("@"))return false;
				fromIA.validate();
				toIA.validate();
			}

			// check from blacklist
			for (Object badDomain : fromBlackList) {
				log.debug(badDomain+"<<<---- Comparing fromBlackList---->>>"+fromIA.getAddress());
				if (fromIA.getAddress().endsWith(badDomain.toString())) {
					log.warn("Rejecting mail from "+from+" to "+recipient+" due to blacklisted FROM:"+badDomain);
					return false;
				}
			}
			// check to blacklist
			for (Object badDomain : toBlackList) {
				log.debug(badDomain+"<<<---- Comparing toBlackList---->>>"+toIA.getAddress());
				if (toIA.getAddress().endsWith(badDomain.toString())) {
					log.warn("Rejecting mail from "+from+" to "+recipient+" due to blacklisted TO:"+badDomain);
					return false;
				}
			}

			// check the from whitelist
			if (fromWhiteList.size()>0) {
				for (Object goodDomain : fromWhiteList) {
					log.debug(goodDomain.toString()+"<<<---- Comparing fromWhiteList---->>>"+fromIA.getAddress());
					if (fromIA.getAddress().endsWith(goodDomain.toString())) {
						return true;
					}
				}
				log.warn("Rejecting mail from "+from+" to "+recipient+" because not in FROM whitelist");
				return false;
			}

			// check the to whitelist
			if (toWhiteList.size()>0) {
				for (Object goodDomain : toWhiteList) {
					log.debug(goodDomain.toString()+"<<<---- Comparing toWhiteList---->>>"+toIA.getAddress());
					if (toIA.getAddress().endsWith(goodDomain.toString())) {
						return true;
					}
				}
				log.warn("Rejecting mail from "+from+" to "+recipient+" because not in TO whitelist");
				return false;
			}


			// else we accept everyone
			log.debug("Accepting mail for "+recipient+" from "+from);
			return true;
		}
		catch (Throwable t) {
			log.error(t.getMessage()+" for from="+from+" and recipient="+recipient);
			errorLog("Accept error for address "+recipient+" sent by "+from, Utils.convertStringToStream(t.toString()));
			//			t.printStackTrace();
			return false;
		}
	}

	@Override
	public void deliver(String from, String recipient, InputStream data) throws TooMuchDataException, IOException {
		recipient = javax.mail.internet.MimeUtility.decodeText(recipient);
		from = javax.mail.internet.MimeUtility.decodeText(from);
		// when reading from .eml files, we might get multiple recipients, not sure why they are delivered like this.
		// when called via SubEtha, they are normally single recipient addresses
		if (recipient.indexOf(',')>=0) {
			StringTokenizer tok = new StringTokenizer(recipient,",");
			List<String> recipients = new ArrayList<String>();
			String r;
			while (tok.hasMoreTokens()) {
				r = tok.nextToken();
				// ensure we remove duplicates
				if ((r.length()>1)&&(!recipients.contains(r))) {
					recipients.add(r);
				}		
				else {
					log.info("Skipping duplicate recipient");
				}
			}
			for (String nrec : recipients) {
				data.mark(Integer.MAX_VALUE);
				deliver(from, nrec, data);
				data.reset();
			}
		}
		else {
			try {
				deliver(from,recipient,Utils.loadEML(data));
			} 
			catch (Throwable e) {
				log.error(e.getMessage());
				errorLog("("+e.getMessage()+") Accepting raw message for recipient="+recipient +" "+e.getMessage(), data);
				e.printStackTrace();
			}
		}

	}

	public void deliver(String from, String recipient, MimeMessage mmessage) throws Exception {
		from = getFullAddress(from, mmessage.getFrom());
		recipient = getFullAddress(recipient, mmessage.getAllRecipients());
		log.info("Delivering mail for "+recipient+" from "+from);
		//		InboxAddress inbox = new InboxAddress(BlueboxMessage.getRecipient(new InboxAddress(recipient), mmessage).toString());
		BlueboxMessage message = StorageFactory.getInstance().store( 
				from,
				new InboxAddress(recipient),
				new Date(),
				mmessage);
		// ensure the content is indexed
		try {
			SearchIndexer.getInstance().indexMail(message);
		}
		catch (Throwable t) {
			log.error(t.getMessage());
			t.printStackTrace();
		}
		updateStats(message, recipient, false);
	}

	/*
	 * When mail is delivered, the personal name is not included, so parse the mail to fill it back in
	 */
	private String getFullAddress(String from,Address[] addresses) {
		try {
			for (int i = 0; i < addresses.length;i++) {
				if (((InternetAddress)addresses[i]).getAddress().equals(from)) {
					return addresses[i].toString();
				}
			}
			return from;
		}
		catch (Throwable t) {
			return from;
		}
	}

	public void updateStats(BlueboxMessage message, String recipient, boolean force) throws AddressException, JSONException {
		incrementGlobalCount();
		if (message!=null)
			updateStatsRecent(message.getInbox().getAddress(),message.getFrom().getString(0),message.getSubject(),message.getIdentifier());	
	}

	public void clearErrors() throws Exception {
		StorageFactory.getInstance().logErrorClear();
	}

	public void errorLog(String title, InputStream is) {
		StorageFactory.getInstance().logError(title, is);
	}

	public String errorDetail(String id) {
		return StorageFactory.getInstance().logErrorContent(id);
	}

	public int errorCount() throws Exception {
		return StorageFactory.getInstance().logErrorCount();
	}

	public JSONArray errorCount(int start, int count) throws Exception {
		return StorageFactory.getInstance().logErrorList(start, count);
	}

	public void setState(String uid, BlueboxMessage.State state) throws Exception {
		StorageFactory.getInstance().setState(uid, BlueboxMessage.State.DELETED);
	}

	public JSONArray autoComplete(String hint, long start, long count) throws Exception {

		JSONObject curr;
		JSONArray children = new JSONArray();
		// no need to include wildcard
		//		if (hint.contains("*")) {
		//			hint=hint.substring(0,hint.indexOf('*'));
		//		}
		if (hint.length()==0)
			hint = "*";
		// ensure we check for all substrings
		if (!hint.startsWith("*"))
			hint = "*"+hint;
		//		if (hint.length()==1)
		//			return children;

		//			hint = QueryParser.escape(hint);
		SearchIndexer search = SearchIndexer.getInstance();
		Document[] results = search.search(hint, SearchIndexer.SearchFields.RECIPIENTS, (int)start, (int)count, SearchIndexer.SearchFields.RECEIVED,false);
		for (int i = 0; i < results.length;i++) {
			String uid = results[i].get(SearchFields.UID.name());
			curr = new JSONObject();
			InboxAddress inbox;
			inbox = new InboxAddress(results[i].get(Utils.decodeRFC2407(SearchFields.INBOX.name())));
			curr.put("name", inbox.getAddress());
			curr.put("label",search.getRecipient(inbox,results[i].get(SearchFields.RECIPIENTS.name())).getFullAddress());
			curr.put("identifier", uid);
			if (!contains(children,curr.getString("name"))) {
				children.put(curr);
			}

			if (children.length()>=count)
				break;
		}

		return children;
	}

	private boolean contains(JSONArray children, String name) {
		for (int i = 0; i < children.length();i++) {
			try {
				if (children.getJSONObject(i).getString("name").equals(name)) {
					return true;
				}
			} 
			catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	public long getStatsGlobalCount() {
		return prefs.getLong(GLOBAL_COUNT_NODE, 0);	
	}

	public void setStatsGlobalCount(long count) {
		prefs.putLong(GLOBAL_COUNT_NODE,count);
	}

	private void incrementGlobalCount() {
		setStatsGlobalCount(getStatsGlobalCount()+1);
	}

	public JSONObject getStatsRecent() {
		return recentStats;
	}


	public JSONObject getStatsActiveInbox() {	
		return StorageFactory.getInstance().getMostActiveInbox();
	}

	public JSONObject getStatsActiveSender() {	
		return StorageFactory.getInstance().getMostActiveSender();
	}

	private JSONObject updateStatsRecent(String inbox, String from, String subject, String uid) {
		try {
			recentStats.put(BlueboxMessage.SUBJECT, subject);
			recentStats.put(BlueboxMessage.INBOX, inbox);
			recentStats.put(BlueboxMessage.FROM, from);
			recentStats.put(BlueboxMessage.UID, uid);
		} 
		catch (JSONException e1) {
			e1.printStackTrace();
		}

		return recentStats;
	}

	public WorkerThread rebuildSearchIndexes() {

		WorkerThread wt = new WorkerThread("reindex") {

			@Override
			public void run() {
				try {
					SearchIndexer searchIndexer = SearchIndexer.getInstance();
					searchIndexer.deleteIndexes();
					long mailCount = getMailCount(BlueboxMessage.State.ANY);
					int start = 0;
					int blocksize = 100;
					while (start<mailCount) {
						log.info("Indexing mail batch "+start+" to "+(start+blocksize)+" of "+mailCount);
						setProgress((int)(start*100/mailCount));
						try {
							List<BlueboxMessage> messages = listInbox(null, BlueboxMessage.State.ANY, start, blocksize, BlueboxMessage.RECEIVED, true);
							for (BlueboxMessage message : messages) {
								searchIndexer.indexMail(message);
							}
						} 
						catch (Exception e) {
							e.printStackTrace();
						}
						start += blocksize;
					}
				} 
				catch (IOException e) {
					e.printStackTrace();
				}
				finally {
					log.info("Finished rebuilding search indexes");
					setProgress(100);
				}

			}

		};
		return wt;
	}

	public void addToWhiteList(String goodDomain) {
		toWhiteList.add(goodDomain);		
	}

	public void addFromWhiteList(String goodDomain) {
		fromWhiteList.add(goodDomain);	
	}

	public void addFromBlacklist(String badDomain) {
		fromBlackList.add(badDomain);		
	}

	public void addToBlacklist(String badDomain) {
		toBlackList.add(badDomain);		
	}

	public void loadConfig() {
		// set up the blacklists and whielists
		fromBlackList = Config.getInstance().getStringList(Config.BLUEBOX_FROMBLACKLIST);
		toBlackList = Config.getInstance().getStringList(Config.BLUEBOX_TOBLACKLIST);
		toWhiteList = Config.getInstance().getStringList(Config.BLUEBOX_TOWHITELIST);
		fromWhiteList = Config.getInstance().getStringList(Config.BLUEBOX_FROMWHITELIST);		
	}

	public WorkerThread runMaintenance() throws Exception {
		return StorageFactory.getInstance().runMaintenance();
	}

	public WorkerThread backup(final File dir) throws Exception {
		log.info("Backing up mail to "+dir.getCanonicalPath());
		final Inbox inbox = Inbox.getInstance();
		WorkerThread wt = new WorkerThread("backup") {

			@Override
			public void run() {
				try {
					List<BlueboxMessage> mail;
					int start = 0;
					int count = 100;
					do {
						setProgress((int)(((start+1)*100)/inbox.getMailCount(BlueboxMessage.State.ANY)));
						mail = inbox.listInbox(null, BlueboxMessage.State.ANY, start, count, BlueboxMessage.RECEIVED, true);
						if (mail.size()>0) {
							log.info("Backing up from "+start+" to "+(start+count));
							File emlFile,jsonFile;
							for (BlueboxMessage msg : mail) {
								try {
									emlFile = new File(dir,msg.getIdentifier()+".eml");
									jsonFile = new File(dir.getCanonicalFile(),msg.getIdentifier()+".json");
									if (jsonFile.exists()||emlFile.exists()) {
										log.info("Skipping backup of "+msg.getIdentifier());
										
									}
									else {
										OutputStream fos = new BufferedOutputStream(new FileOutputStream(emlFile));
										Utils.copy(Utils.streamMimeMessage(msg.getBlueBoxMimeMessage()),fos);
										fos.close();

										fos = new BufferedOutputStream(new FileOutputStream(jsonFile));
										fos.write(msg.toJSON().toString().getBytes());
										fos.close();
									}
								}
								catch (Throwable t) {
									log.warn(t.getMessage());
								}
							}
						}
						start += mail.size();

					} while (mail.size()>0);	
				}
				catch (Throwable t) {
					t.printStackTrace();
				}
				finally {
					setProgress(100);					
				}
			}

		};

		return wt;
	}

	public WorkerThread restore(final File dir) throws Exception {
		log.info("Restoring mail from {}",dir.getCanonicalPath());
		WorkerThread wt = new WorkerThread("restore") {

			@Override
			public void run() {
				if (dir.exists()) {
					File[] files = dir.listFiles();
					for (int i = 0; i < files.length;i++) {
						setProgress(i*100/files.length);
						log.debug("Progress : {}",(i*100/files.length));
						if (files[i].getName().endsWith("eml")) {
							try {
								JSONObject jo = new JSONObject(FileUtils.readFileToString(new File(files[i].getCanonicalPath().substring(0, files[i].getCanonicalPath().length()-4)+".json")));
								// backwards compat workaround for backups prior to introduction of RECIPIENT field
								if (!jo.has(BlueboxMessage.RECIPIENT)) {
									jo.put(BlueboxMessage.RECIPIENT,jo.get(BlueboxMessage.INBOX));
								}
								else {
									// if it's there, but is a JSONarray, use value of inbox instead
									if (jo.get(BlueboxMessage.RECIPIENT) instanceof JSONArray) {
										// try get actual full name version from this array
										JSONArray ja = jo.getJSONArray(BlueboxMessage.RECIPIENT);
										jo.put(BlueboxMessage.RECIPIENT,jo.get(BlueboxMessage.INBOX));
										for (int j = 0; j < ja.length(); j++) {
											if (ja.getString(j).indexOf(jo.getString(BlueboxMessage.INBOX))>=0) {
												jo.put(BlueboxMessage.RECIPIENT,ja.getString(j));
												break;
											}
										}
									}
								}

								InputStream ms = new BufferedInputStream(new FileInputStream(files[i]));
								ms.mark(Integer.MAX_VALUE);
								StorageFactory.getInstance().store(jo, ms);
								ms.reset();
								MimeMessage mm = Utils.loadEML(ms);
								SearchIndexer.getInstance().indexMail(new BlueboxMessage(jo,mm));
							}
							catch (Throwable t) {
								t.printStackTrace();
								log.warn(t.getMessage());
							}
						}
					}
				}
				else {
					log.error("Could not access");
				}
				setProgress(100);
			}

		};
		return wt;

	}


}
