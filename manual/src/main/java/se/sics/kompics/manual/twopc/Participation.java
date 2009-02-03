package se.sics.kompics.manual.twopc;

import se.sics.kompics.PortType;
import se.sics.kompics.manual.twopc.event.Abort;
import se.sics.kompics.manual.twopc.event.Ack;
import se.sics.kompics.manual.twopc.event.Commit;
import se.sics.kompics.manual.twopc.event.Prepare;
import se.sics.kompics.manual.twopc.event.Rollback;

public final class Participation extends PortType {
	{
		positive(Commit.class);
		positive(Abort.class);
		positive(Ack.class);

		negative(Prepare.class);
		negative(Commit.class);
		negative(Rollback.class);		
	}
}