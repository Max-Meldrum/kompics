/**
 * This file is part of the ID2210 course assignments kit.
 * 
 * Copyright (C) 2009 Royal Institute of Technology (KTH)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.kompics.kdld.master;

import se.sics.kompics.address.Address;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 * The <code>CacheEvictPeer</code> class.
 * 
 * @author Cosmin Arad <cosmin@sics.se>
 */
public final class EvictDaemon extends Timeout {

	private final Address peerAddress;

	private final int jobId;
	
	private final long epoch;

	public EvictDaemon(ScheduleTimeout request, Address peerAddress, int jobId,
			long epoch) {
		super(request);
		this.peerAddress = peerAddress;
		this.jobId = jobId;
		this.epoch = epoch;
	}

	public Address getPeerAddress() {
		return peerAddress;
	}

	public int getJobId() {
		return jobId;
	}
	
	public long getEpoch() {
		return epoch;
	}
}