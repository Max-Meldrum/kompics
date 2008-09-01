package se.sics.kompics.p2p.network;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sics.kompics.api.Channel;
import se.sics.kompics.api.Component;
import se.sics.kompics.api.ComponentMembrane;
import se.sics.kompics.api.EventAttributeFilter;
import se.sics.kompics.api.Priority;
import se.sics.kompics.api.annotation.ComponentCreateMethod;
import se.sics.kompics.api.annotation.ComponentInitializeMethod;
import se.sics.kompics.api.annotation.ComponentShareMethod;
import se.sics.kompics.api.annotation.ComponentSpecification;
import se.sics.kompics.api.annotation.EventHandlerMethod;
import se.sics.kompics.api.annotation.MayTriggerEventTypes;
import se.sics.kompics.network.Address;
import se.sics.kompics.network.events.Message;
import se.sics.kompics.p2p.network.events.PerfectNetworkAlarm;
import se.sics.kompics.p2p.network.events.PerfectNetworkMessage;
import se.sics.kompics.p2p.network.topology.KingMatrix;
import se.sics.kompics.timer.events.SetAlarm;

/**
 * The <code>PeerMonitor</code> class
 * 
 * @author Cosmin Arad
 * @version $Id$
 */
@ComponentSpecification
public final class PerfectNetwork {

	private Logger logger;

	private Component component;

	// PerfectNetwork channels
	private Channel sendChannel, deliverChannel;

	// timer channels
	private Channel timerSetChannel, timerSignalChannel;

	// network channels
	private Channel netSendChannel, netDeliverChannel;

	private Address localAddress;

	private int[][] king = KingMatrix.KING;

	private int localKingId;

	public PerfectNetwork(Component component) {
		this.component = component;
	}

	@ComponentCreateMethod
	public void create(Channel sendChannel, Channel deliverChannel) {
		this.sendChannel = sendChannel;
		this.deliverChannel = deliverChannel;

		// use shared timer component
		ComponentMembrane timerMembrane = component
				.getSharedComponentMembrane("se.sics.kompics.Timer");
		timerSetChannel = timerMembrane.getChannelIn(SetAlarm.class);

		// use a private channel for TimerSignal events
		timerSignalChannel = component.createChannel(PerfectNetworkAlarm.class);

		// use shared network component
		ComponentMembrane networkMembrane = component
				.getSharedComponentMembrane("se.sics.kompics.Network");
		netSendChannel = networkMembrane.getChannelIn(Message.class);
		netDeliverChannel = networkMembrane.getChannelOut(Message.class);

		component.subscribe(timerSignalChannel, "handlePerfectNetworkAlarm");
		component.subscribe(this.sendChannel, "handleMessage");
	}

	@ComponentShareMethod
	public ComponentMembrane share(String name) {
		ComponentMembrane membrane = new ComponentMembrane(component);
		membrane.inChannel(Message.class, sendChannel);
		membrane.outChannel(Message.class, deliverChannel);
		return component.registerSharedComponentMembrane(name, membrane);
	}

	@ComponentInitializeMethod
	public void init(Address localAddress) {
		this.localAddress = localAddress;

		logger = LoggerFactory.getLogger(PerfectNetwork.class.getName() + "@"
				+ localAddress.getId());

		BigInteger kingId = localAddress.getId().mod(
				BigInteger.valueOf(KingMatrix.SIZE));

		this.localKingId = kingId.intValue();

		EventAttributeFilter destinationFilter = new EventAttributeFilter(
				"destination", localAddress);

		component.subscribe(netDeliverChannel, "handlePerfectNetworkMessage",
				destinationFilter);
		logger.debug("Subscribed for PerfectNetNetDeliver with destination {}",
				localAddress);
	}

	@EventHandlerMethod
	@MayTriggerEventTypes( { SetAlarm.class, Message.class })
	public void handleMessage(Message event) {
		logger.debug("Handling send1 {} to {}.", event, event.getDestination());

		event.setSource(localAddress);
		Address destination = event.getDestination();

		if (destination.equals(localAddress)) {
			// deliver locally
			// PerfectNetworkDeliverEvent deliverEvent = event
			// .getPerfectNetworkDeliverEvent();
			// deliverEvent.setSource(localAddress);
			// deliverEvent.setDestination(destination);
			component.triggerEvent(event, deliverChannel);
			return;
		}

		// make a PerfectNetNetworkDeliverEvent to be delivered at the
		// destination
		PerfectNetworkMessage pnMessage = new PerfectNetworkMessage(event,
				localAddress, destination);

		long latency = king[localKingId][destination.getId().mod(
				BigInteger.valueOf(KingMatrix.SIZE)).intValue()];
		if (latency > 0) {
			// delay the sending according to the latency
			PerfectNetworkAlarm tse = new PerfectNetworkAlarm(pnMessage);
			SetAlarm ste = new SetAlarm(0, tse, timerSignalChannel, component,
					latency);
			component.triggerEvent(ste, timerSetChannel, Priority.HIGH);
		} else {
			// send immediately
			component.triggerEvent(pnMessage, netSendChannel);
		}
	}

	@EventHandlerMethod
	@MayTriggerEventTypes(Message.class)
	public void handlePerfectNetworkAlarm(PerfectNetworkAlarm event) {
		logger.debug("Handling send2 {} to {}.", ((PerfectNetworkMessage) event
				.getMessage()).getMessage(), event.getMessage()
				.getDestination());

		component.triggerEvent(event.getMessage(), netSendChannel);
	}

	@EventHandlerMethod
	@MayTriggerEventTypes(Message.class)
	public void handlePerfectNetworkMessage(PerfectNetworkMessage event) {
		logger.debug("Handling delivery {} from {}.", event.getMessage(), event
				.getSource());

		Message pnDeliverEvent = event.getMessage();
		// pnDeliverEvent.setSource(event.getSource());
		// pnDeliverEvent.setDestination(event.getDestination());

		// trigger the encapsulated PerfectNetworkDeliverEvent
		component.triggerEvent(pnDeliverEvent, deliverChannel);
	}
}