package se.sics.kompics.manual.twopc.composite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.manual.twopc.Client;
import se.sics.kompics.manual.twopc.TwoPhaseCommit;
import se.sics.kompics.manual.twopc.event.Abort;
import se.sics.kompics.manual.twopc.event.Ack;
import se.sics.kompics.manual.twopc.event.BeginTransaction;
import se.sics.kompics.manual.twopc.event.Commit;
import se.sics.kompics.manual.twopc.event.CommitTransaction;
import se.sics.kompics.manual.twopc.event.CoordinatorInit;
import se.sics.kompics.manual.twopc.event.Operation;
import se.sics.kompics.manual.twopc.event.Prepare;
import se.sics.kompics.manual.twopc.event.Prepared;
import se.sics.kompics.manual.twopc.event.ReadOperation;
import se.sics.kompics.manual.twopc.event.RollbackTransaction;
import se.sics.kompics.manual.twopc.event.TransResult;
import se.sics.kompics.manual.twopc.event.Transaction;
import se.sics.kompics.manual.twopc.event.WriteOperation;
import se.sics.kompics.timer.Timer;

/**
 * <h2>Two-phase-commit protocol</h2> 
 * This is a toy implementation of 2PC for showing features of Kompics.
 * Do not use it as a reference for a 2PC implementation.
 * 
 * The coordinator implementation below performs the following: 
 * On receiving a BeginTransaction message, it creates an activeTransaction. Any read
 * or write operations for that transaction are buffered here at the 
 * coordinator (note: this is incorrect 2PC behaviour). When a client sends
 * a CommitTransaction, the set of read/write operations are sent in a Transaction
 * object to the participants as a Prepare message. The participants respond
 * with either a Prepared or Abort messaage. If all participants respond with
 * a Prepared message, the coordinator sends a Commit message to the participants,
 * otherwise the transaction is aborted (and a TransResult failure message is
 * sent to the client). The participants respond to the Commit message with an
 * Ack message, and when all Acks have been received by the coordinator, it sends
 * a Transaction Result message back to the client.
 * 
 * A more correct description of 2PC is given below. 
 * <h3>(Phase 1) Commit-request phase</h3>
 * <ul>
 * <li>1. The coordinator sends a Prepare message to all participants.</li>
 * <li>2. The participants execute the transaction up to the point where they
 * will be asked to commit. They each write an entry to their undo log and an
 * entry to their redo log.</li>
 * <li>3. Each participant replies with an agreement message if the transaction
 * succeeded, or an abort message if the transaction failed.</li>
 * <li>4. The coordinator waits until it has a message from each participant.</li>
 * </ul>
 * 
 * <h3>(Phase 2) Commit phase</h3> If the coordinator received an agreement
 * message from all participants during the commit-request phase:
 * <ul>
 * <li>1. The coordinator writes a commit record into its log.</li>
 * <li>2. The coordinator sends a commit message to all the participants.</li>
 * <li>3. Each participant completes the operation, and releases all the locks
 * and resources held during the transaction.</li>
 * <li>4. Each participant sends an acknowledgement to the coordinator.</li>
 * <li>5. The coordinator completes the transaction when acknowledgements have
 * been received.</li>
 * </ul>
 * If any participant sent an abort message during the commit-request phase:
 * <ul>
 * <li>1. The coordinator sends an rollback message to all the participants.</li>
 * <li>2. Each participant undoes the transaction using the undo log, and
 * releases the resources and locks held during the transaction.</li>
 * <li>3. Each participant sends an acknowledgement to the coordinator.</li>
 * <li>4. The coordinator completes the transaction when acknowledgements have
 * been received.</li>
 * </ul>
 */
public class Coordinator extends ComponentDefinition {

	Negative<Client> coordinator = negative(Client.class);

	Positive<TwoPhaseCommit> tpcPort = positive(TwoPhaseCommit.class);

	Positive<Timer> timer = positive(Timer.class);

	private int id;

	private Address self;

	private Map<Integer, Address> mapParticipants;

	private Map<Integer, Integer> tranVotes = new HashMap<Integer, Integer>();
	private Map<Integer, Integer> tranAcks = new HashMap<Integer, Integer>();

	private Map<Integer, List<Operation>> activeTransactions = new HashMap<Integer, List<Operation>>();

	private static final Logger logger = LoggerFactory
			.getLogger(Coordinator.class);

	public Coordinator() {
		subscribe(handleCoordinatorInit, control);

		subscribe(handleBeginTransaction, coordinator);
		subscribe(handleCommitTransaction, coordinator);
		subscribe(handleRollbackTransaction, coordinator);
		subscribe(handleReadOperation, coordinator);
		subscribe(handleWriteOperation, coordinator);

		subscribe(handlePrepared, tpcPort);
		subscribe(handleAbort, tpcPort);
		subscribe(handleAck, tpcPort);
	}

	Handler<CoordinatorInit> handleCoordinatorInit = new Handler<CoordinatorInit>() {
		public void handle(CoordinatorInit init) {
			id = init.getId();
			logger.info("Coordinator with id: " + id);
			self = init.getSelf();
			mapParticipants = init.getMapParticipants();
		}
	};

	Handler<BeginTransaction> handleBeginTransaction = new Handler<BeginTransaction>() {
		public void handle(BeginTransaction trans) {
			logger.info("Coordinator client: begin transaction at "
					+ trans.getTransactionId());
			List<Operation> ops = new ArrayList<Operation>();
			activeTransactions.put(trans.getTransactionId(), ops);

			logger.info("TRANS VOTES is NOW: (" + trans.getTransactionId()
					+ ", 0)");
			tranVotes.put(trans.getTransactionId(), 0);
		}
	};

	Handler<CommitTransaction> handleCommitTransaction = new Handler<CommitTransaction>() {
		public void handle(CommitTransaction trans) {
			logger.info("Coordinator client: commit transaction "
					+ trans.getTransactionId());

			// Start Two-Phase Commit with Participants
			List<Operation> ops = activeTransactions.get(trans
					.getTransactionId());

			for (Address dest : mapParticipants.values()) {
				Transaction t = new Transaction(trans.getTransactionId(),
						Transaction.CommitType.COMMIT, ops);

				logger.info("Coordinator: sending prepare to: "
						+ dest.toString());
				trigger(new Prepare(t, self, dest), tpcPort);
			}
		}
	};

	// This handler doesn't rollback operations (yet), it just aborts the transaction buffered
	// here at the coordinator
	Handler<RollbackTransaction> handleRollbackTransaction = new Handler<RollbackTransaction>() {
		public void handle(RollbackTransaction trans) {
			logger.info("Coordinator client: rollback transaction "
					+ trans.getTransactionId());
			for (Address dest : mapParticipants.values()) {
				trigger(new Abort(trans.getTransactionId(), self, dest),
						tpcPort);
			}
		}
	};

	Handler<ReadOperation> handleReadOperation = new Handler<ReadOperation>() {
		public void handle(ReadOperation readOp) {
			logger.info("Coordinator client: read operation "
					+ readOp.getTransactionId());

			List<Operation> ops;
			if (activeTransactions.containsKey(readOp.getTransactionId()) == false) {
				ops = new ArrayList<Operation>();
				activeTransactions.put(readOp.getTransactionId(), ops);
			}

			// Add operation to its active transaction
			ops = activeTransactions.get(readOp.getTransactionId());
			ops.add(readOp);
			// TODO send read to participants and result to client, acquire read-lock
		}
	};

	Handler<WriteOperation> handleWriteOperation = new Handler<WriteOperation>() {
		public void handle(WriteOperation writeOp) {
			logger.info("Coordinator client: write operation "
					+ writeOp.getTransactionId());

			List<Operation> ops;
			if (activeTransactions.containsKey(writeOp.getTransactionId()) == false) {
				ops = new ArrayList<Operation>();
				activeTransactions.put(writeOp.getTransactionId(), ops);
			}
			ops = activeTransactions.get(writeOp.getTransactionId());
			ops.add(writeOp);
		}
	};

	// Prepared is sent from every Participant to the Coordinator
	Handler<Prepared> handlePrepared = new Handler<Prepared>() {
		public void handle(Prepared commit) {
			int tId = commit.getTransactionId();

			if (tranVotes.get(tId) == null) {
				logger.error("TRANS VOTES WAS NULL for transaction-id:" + tId);
				return;
			}

			if (tranVotes.get(tId) == -1) {
				// do nothing - transaction already aborted
				return;
			}

			tranVotes.put(tId, tranVotes.get(tId) + 1);

			if (tranVotes.get(tId) == mapParticipants.size()) {
				tranAcks.put(commit.getTransactionId(), 0);
				for (Address dest : mapParticipants.values()) {
					logger.info("Coordinator: sending commit to: "
							+ dest.toString());
					trigger(new Commit(tId, self, dest), tpcPort);
				}
			}
		}
	};

	Handler<Abort> handleAbort = new Handler<Abort>() {
		public void handle(Abort abort) {
			logger.info("Coordinator abort recvd " + abort.getTransactionId());
			// transaction aborted
			tranVotes.put(abort.getTransactionId(), -1);
			trigger(new TransResult(abort.getTransactionId(), false),
					coordinator);
		}
	};

	Handler<Ack> handleAck = new Handler<Ack>() {
		public void handle(Ack ack) {
			int tId = ack.getTransactionId();
			logger.info("Coordinator ack recvd " + tId);

			tranAcks.put(tId, tranAcks.get(tId) + 1);
			if (tranAcks.get(tId) == mapParticipants.size()) {
				TransResult res = new TransResult(ack.getTransactionId(), true);

				if (ack.getResponses().size() > 0) {
					res.setResponses(ack.getResponses());
				}
				trigger(res, coordinator);
			}
		}
	};
}