package org.jscsi.target.connection;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jscsi.target.conf.OperationalTextConfiguration;
import org.jscsi.target.conf.OperationalTextException;
import org.jscsi.target.connection.Connection;
import org.jscsi.target.parameter.connection.Phase;
import org.jscsi.target.parameter.connection.SessionType;
import org.jscsi.connection.SerialArithmeticNumber;
import org.jscsi.parser.InitiatorMessageParser;
import org.jscsi.parser.ProtocolDataUnit;
import org.jscsi.parser.TargetMessageParser;
import org.jscsi.parser.login.ISID;
import org.jscsi.parser.login.LoginRequestParser;
import org.jscsi.parser.snack.SNACKRequestParser;

/**
 * <h1>Session</h1>
 * <p/>
 * 
 * A session or Initiator Target Nexus is a directed communication from an iSCSI
 * Initiator to an iSCSI Target. Each session can contains of several
 * connections. This allows a better usage of bandwidth and decreases latency
 * times.
 * 
 * @author Marcus Specht
 * 
 */
public class Session {

	/** The Logger interface. */
	private static final Log LOGGER = LogFactory.getLog(Session.class);

	public static final String IDENTIFIER = "Session";

	public static final String TARGET_SESSION_IDENTIFYING_HANDLE = "TargetSessionIdentifyingHandle";

	public static final String INITIATOR_SESSION_ID = "InitiatorSessionID";

	public static final String INITIATOR_NAME = "InitiatorName";

	// --------------------------------------------------------------------------
	// --------------------------------------------------------------------------

	/** The <code>Configuration</code> instance for this session. */
	private final OperationalTextConfiguration configuration;

	/** Session's Phase */
	private short SessionPhase;

	/** The Target Session Identifying Handle. */
	private short targetSessionIdentifyingHandle;

	/** The Initiator Session ID */
	private ISID initiatorSessionID;

	private String initiatorName;

	/** the session's type */
	private SessionType sessionType;

	/** The Command Sequence Number of this session. */
	private SerialArithmeticNumber expectedCommandSequenceNumber;

	/** The Maximum Command Sequence Number of this session. */
	private SerialArithmeticNumber maximumCommandSequenceNumber;

	/** connections are mapped to their receiving Queues */
	private final Map<Connection, Queue<ProtocolDataUnit>> connections;

	private final Map<Integer, Connection> signalledPDUs;

	private final Queue<ProtocolDataUnit> receivedPDUs;


	public Session(Connection connection, short tsih) throws OperationalTextException {
		configuration = OperationalTextConfiguration.create(this);
		targetSessionIdentifyingHandle = tsih;
		connections = new ConcurrentHashMap<Connection, Queue<ProtocolDataUnit>>();
		signalledPDUs = new ConcurrentHashMap<Integer, Connection>();
		receivedPDUs = new ConcurrentLinkedQueue<ProtocolDataUnit>(); 
		addConnection(connection);
		// start TaskManger
	}

	/**
	 * Adds a Connection to this Session.
	 * 
	 * @param connection
	 */
	public void addConnection(Connection connection) {
		if ((!connections.containsKey(connection))
				&& (connection.setReferencedSession(this))) {
			connections.put(connection, connection.getReceivingQueue());
		} else {
			if (LOGGER.isDebugEnabled()) {
				LOGGER
				.debug("Tried to add a Connection which has already a referenced Session!");
			}
		}

	}

	/**
	 * Checks based on a Session's ISID and TSIH if those parameters are equals
	 * this session's ones. If so, the source <code>Connection</code> should
	 * be added to this <code>Session</code> instance.
	 * 
	 * @param isid
	 *            a Connection's Initiator Session ID
	 * @param tsih
	 *            a Session's Target Session Identifying Handle
	 * @return true if parameters are equal, false else.
	 */
	final boolean checkAppropriateConnection(ISID isid, short tsih) {
		if (isid.equals(initiatorSessionID)
				&& (tsih == targetSessionIdentifyingHandle)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean containsConnection(Connection connection) {
		return connections.containsKey(connection);
	}

	/**
	 * An iterator over all existing <code>Connection</code>s within this
	 * Session
	 * 
	 * @return all existing <code>Connections</code>
	 */
	public Iterator<Connection> getConnections() {
		return connections.keySet().iterator();
	}

	/**
	 * Returns a <code>Connection</code> with specified connectionID. If not
	 * found, returns null.
	 * 
	 * @param connectionID
	 *            the Connection's ID
	 * @return an existing COnnection or null, if not found
	 */
	public Connection getConnection(short connectionID) {

		Iterator<Connection> testedCons = connections.keySet().iterator();
		while (testedCons.hasNext()) {
			Connection tested = testedCons.next();
			if (tested.getConnectionID() == connectionID) {
				return tested;
			}
		}
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("No Connection found with follwing connection ID: "
					+ connectionID);
		}
		return null;
	}

	public final OperationalTextConfiguration getConfiguration() {
		return configuration;
	}

	/**
	 * Returns the Session's Initiators Session ID.
	 * 
	 * @return
	 */
	final ISID getInitiatorSessionID() {
		return initiatorSessionID;
	}

	/**
	 * Returns the Session's Initiator Name.
	 * 
	 * @return
	 */
	final String getInitiatorName() {
		return initiatorName;
	}

	/**
	 * Returns the Session's actual expected command sequence number.
	 * 
	 * @return SerialArithmeticNumber representing the session's expCmdSeqNum
	 */
	final SerialArithmeticNumber getExpectedCommandSequence() {
		return getExpectedCommandSequenceNumber(false);
	}
	
	/**
	 * Returns the Session's actual Expected Command Sequence Number and if
	 * true, increments the expCmdSeqNum before returning.
	 * 
	 * @param inkr
	 *            if true, increments before return, else only return
	 * @return SerialArithmeticNumber the Session's expCmdSeqNum
	 */
	private final SerialArithmeticNumber getExpectedCommandSequenceNumber(boolean incr) {
		synchronized (expectedCommandSequenceNumber) {
			if (incr == true) {
				expectedCommandSequenceNumber.increment();
			}
			return expectedCommandSequenceNumber;
		}
	}

	/**
	 * Returns the Session's actual Maximum Command Sequence Number.
	 * 
	 * @return SerialArithmeticNumber representing the session's maxCmdSeqNum
	 */
	final SerialArithmeticNumber getMaximumCommandSequence() {
		return incrMaximumCommandSequence(0);
	}

	public final SessionType getSessionType() {
		return sessionType;
	}

	/**
	 * Returns the Session's Target Identifying Handle.
	 * 
	 * @return
	 */
	final short getTargetSessionIdentifyingHandleD() {
		return targetSessionIdentifyingHandle;
	}

	
	
	/**
	 * Increments the Session's ExpCmdSN if necessary because of the callingPDU. 
	 * @param callingPDU increment is based on type and state of the callingPDU 
	 */
	final void incrExpectedCommandSequenceNumber(ProtocolDataUnit callingPDU){
		InitiatorMessageParser parser = (InitiatorMessageParser) callingPDU.getBasicHeaderSegment()
		.getParser();
		int receivedCommandSequenceNumber = parser.getCommandSequenceNumber();
		if(getExpectedCommandSequence().compareTo(receivedCommandSequenceNumber) == 0){
			//callingPDU is expected PDU
			if(!(callingPDU.getBasicHeaderSegment().isImmediateFlag())
					&& !(parser instanceof SNACKRequestParser) && !(parser instanceof LoginRequestParser)){
				//ExpCmdSN increment is necessary for callingPDU
				getExpectedCommandSequenceNumber(true);
			}
		}
	}
	
	/**
	 * Returns the Session's actual Maximum Command Sequence Number and if sumX
	 * greater 0, increments the maxCmdSeqNum x times before returning
	 * 
	 * @param sumX
	 *            increment maxCmdSeqNum x times before returning
	 * @return SerialArithmeticNumber representing the session's maxCmdSeqNum
	 */
	final SerialArithmeticNumber incrMaximumCommandSequence(int incrXTimes) {
		if (incrXTimes < 0) {
			incrXTimes = 0;
		}
		synchronized (maximumCommandSequenceNumber) {
			for (int i = 0; i < incrXTimes; i++) {
				maximumCommandSequenceNumber.increment();
			}
			return maximumCommandSequenceNumber;
		}

	}

	/**
	 * Set this Session's Initiator Session ID, if not already set.
	 * 
	 * @param isid
	 *            InitiatorSessionID
	 * @return false if already set, true else.
	 */
	final boolean setInitiatorSessionID(ISID isid) {
		if (initiatorSessionID != null) {
			initiatorSessionID = isid;
			return true;
		}
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Tried to set a session's ISID twice: old ISID is "
					+ getInitiatorSessionID() + ", new ISID would be " + isid);
		}
		return false;
	}

	final boolean setInitiatorName(String name) {
		if (initiatorName != null) {
			initiatorName = name;
			return true;
		}
		if (LOGGER.isDebugEnabled()) {
			LOGGER
			.debug("Tried to set a session's initiatorn name twice: old name is "
					+ getInitiatorName()
					+ ", new name would be "
					+ name);
		}
		return false;
	}

	public final void setSessionType(SessionType type) {
		sessionType = type;
	}

	/*
	final void signalReceivedPDU(Connection connection) {
		// if the received PDU's CmdSN is equal ExpCmdSN, add to received
		// PDUs
		ProtocolDataUnit pdu = null;
		InitiatorMessageParser parser = null;
		SerialArithmeticNumber receivedCommandSequenceNumber = null;
		synchronized (connections) {
			// if the received PDU's CmdSN is equal ExpCmdSN, add to received
			// PDUs
			if (connection.getReceivingQueue().size() > 0) {
				pdu = connections.get(connection).peek();

				if (receivedCommandSequenceNumber
						.equals(expectedCommandSequenceNumber)) {
					receivedPDUs.add(connections.get(connection).poll());
					// only increment ExpCmdSN if non-immediate and no SNACK
					// Request
					if (!pdu.getBasicHeaderSegment().isImmediateFlag()
							&& !(parser instanceof SNACKRequestParser)) {
						expectedCommandSequenceNumber.increment();
					}

					// signal TaskRouter
				} else {

				}

				if (receivedCommandSequenceNumber
						.equals(expectedCommandSequenceNumber)) {
					receivedPDUs.add(connections.get(connection).poll());
					expectedCommandSequenceNumber.increment();
					// new maximum command sequence number
					// signal TaskRouter
				} else {

				}

				// search for the next buffered PDU in every connection
				for (Queue<ProtocolDataUnit> pdus : connections.values()) {
					if (pdus.size() <= 0) {
						continue;
					}
					// could be more than one following PDU per connection
					boolean testing = true;
					while (testing) {
						pdu = pdus.peek();
						parser = (InitiatorMessageParser) pdu
						.getBasicHeaderSegment().getParser();
						receivedCommandSequenceNumber = new SerialArithmeticNumber(
								parser.getCommandSequenceNumber());
						if (receivedCommandSequenceNumber
								.equals(expectedCommandSequenceNumber)) {
							receivedPDUs
							.add(connections.get(connection).poll());
							expectedCommandSequenceNumber.increment();
							// new maximum command sequence number
							// signal TaskRouter
						} else {
							// head of connection's queue isn't the next
							// received
							// PDU
							testing = false;
						}
					}
				}
			}
		}
	}
	*/

	/**
	 * Signaling a sequencing Gap within a connection.
	 * @param connection
	 */
	final void signalCommandSequenceGap(Connection connection){
		//a Connection is signaling a command sequencing gap,
		//which means the connection is receiving more and more PDUs,
		//but the Session will not process them, because ExpCmdSN doesn't
		//reaches the necessary value.
		//The Session can decide whether to clear the connection's receiving PDU buffer
		//(clearing the buffer will only force initiator to send PDUs again, no lost if initiator behaves correct),
		//or to completely drop the connection.
	}

	/**
	 * A connection is signaling a received PDU.
	 * @param CmdSN the received PDU's CommandSequenceNumber
	 * @param connection the connection the PDU was received
	 */
	final void signalReceivedPDU(Integer CmdSN, Connection connection){
		signalledPDUs.put(CmdSN, connection);
		//normal operation
		while(signalledPDUs.containsKey(getExpectedCommandSequence().getValue())){
			synchronized(receivedPDUs){
				//increment ExpCmdSN
				incrExpectedCommandSequenceNumber(connections.get(getExpectedCommandSequence()).peek());
				//MaxCmdSN Window
				receivedPDUs.add(connections.get(getExpectedCommandSequence()).poll());
			}
			//signal TaskRouter
		}
		

	}
	
	
	/**
	 * Updating Session wide, PDU relevant parameters before sending PDU
	 * over specified Connection
	 * @param connection the sending Connection
	 * @param pdu the sending PDU
	 */
	final void sendPDU(Connection connection, ProtocolDataUnit pdu) {
		synchronized (connections) {
			// update PDU parameter, ExpCmdSN + MaxCmdSN
			TargetMessageParser parser = (TargetMessageParser) pdu
			.getBasicHeaderSegment().getParser();
			parser
			.setExpectedCommandSequenceNumber(getExpectedCommandSequence()
					.getValue());
			parser.setMaximumCommandSequenceNumber(getMaximumCommandSequence()
					.getValue());
			// send PDU
			connection.sendPDU(this, pdu);
		}

	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
		+ ((initiatorName == null) ? 0 : initiatorName.hashCode());
		result = prime
		* result
		+ ((initiatorSessionID == null) ? 0 : initiatorSessionID
				.hashCode());
		result = prime * result + targetSessionIdentifyingHandle;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Session other = (Session) obj;
		if (initiatorName == null) {
			if (other.initiatorName != null)
				return false;
		} else if (!initiatorName.equals(other.initiatorName))
			return false;
		if (initiatorSessionID == null) {
			if (other.initiatorSessionID != null)
				return false;
		} else if (!initiatorSessionID.equals(other.initiatorSessionID))
			return false;
		if (targetSessionIdentifyingHandle != other.targetSessionIdentifyingHandle)
			return false;
		return true;
	}

}
