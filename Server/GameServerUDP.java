import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;
import tage.networking.server.GameConnectionServer;
import tage.networking.server.IClientInfo;

public class GameServerUDP extends GameConnectionServer<UUID> {
	public GameServerUDP(int localPort) throws IOException {
		super(localPort, ProtocolType.UDP);
	}

	@Override
	public void processPacket(Object o, InetAddress senderIP, int senderPort) {
		String message = (String) o;

		if (o == null) {
			System.out.println("Warning: Received null packet - ignoring");
			return;
		}

		if (message == null || message.trim().isEmpty()) {
			System.out.println("Warning: Received empty message - ignoring");
			return;
		}

		String[] messageTokens = message.split(",");

		if (messageTokens.length > 0) { // JOIN -- Case where client just joined the server
										// Received Message Format: (join,localId)
			if (messageTokens[0].compareTo("join") == 0) {
				try {
					IClientInfo ci;
					ci = getServerSocket().createClientInfo(senderIP, senderPort);
					UUID clientID = UUID.fromString(messageTokens[1]);
					addClient(ci, clientID);
					System.out.println("Join request received from - " + clientID.toString());
					sendJoinedMessage(clientID, true);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			// BYE -- Case where clients leaves the server
			// Received Message Format: (bye,localId)
			if (messageTokens[0].compareTo("bye") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				System.out.println("Exit request received from - " + clientID.toString());
				sendByeMessages(clientID);
				removeClient(clientID);
			}

			// CREATE -- Case where server receives a create message (to specify avatar
			// location)
			// Received Message Format: (create,localId,x,y,z)
			if (messageTokens[0].compareTo("create") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				String[] pos = { messageTokens[2], messageTokens[3], messageTokens[4] };

				String textureName = "frog.png"; // Default
				if (messageTokens.length > 5) {
					textureName = messageTokens[5];
				}

				sendCreateMessages(clientID, pos, textureName);
				sendWantsDetailsMessages(clientID);
			}

			// DETAILS-FOR --- Case where server receives a details for message
			// Received Message Format: (dsfr,remoteId,localId,x,y,z)
			if (messageTokens[0].compareTo("dsfr") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				UUID remoteID = UUID.fromString(messageTokens[2]);
				String[] pos = { messageTokens[3], messageTokens[4], messageTokens[5] };

				String textureName = "frog.png"; // Default
				if (messageTokens.length > 6) {
					textureName = messageTokens[6];
				}

				sendDetailsForMessage(clientID, remoteID, pos, textureName);
			}

			// MOVE --- Case where server receives a move message
			// Received Message Format: (move,localId,x,y,z)
			if (messageTokens[0].compareTo("move") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				String[] pos = { messageTokens[2], messageTokens[3], messageTokens[4] };
				sendMoveMessages(clientID, pos);
			}

			// ROTATE --- Case where server receives a rotation message
			// Received Message Format:
			// (rotate,localId,r00,r01,r02,r03,r10,r11,r12,r13,r20,r21,r22,r23,r30,r31,r32,r33)
			if (messageTokens[0].compareTo("rotate") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				// Extract the rotation matrix values (all 16 values)
				String[] rotMatrix = new String[16];
				for (int i = 0; i < 16; i++) {
					rotMatrix[i] = messageTokens[i + 2];
				}
				sendRotateMessages(clientID, rotMatrix);
			}

			// ^ BALLS

			// Handle createBall messages
			if (messageTokens[0].compareTo("createBall") == 0) {
				// Format: createBall, senderId, ballId, x, y, z
				UUID clientID = UUID.fromString(messageTokens[1]);
				UUID ballID = UUID.fromString(messageTokens[2]);
				String[] pos = { messageTokens[3], messageTokens[4], messageTokens[5] };

				sendCreateBallMessages(clientID, ballID, pos);
			}

			// Handle moveBall messages
			if (messageTokens[0].compareTo("moveBall") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				UUID ballID = UUID.fromString(messageTokens[2]);
				String[] pos = { messageTokens[3], messageTokens[4], messageTokens[5] };

				sendMoveBallMessages(clientID, ballID, pos);
			}

			// Handle removeBall messages
			if (messageTokens[0].compareTo("removeBall") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				UUID ballID = UUID.fromString(messageTokens[2]);

				sendRemoveBallMessages(clientID, ballID);
			}

			if (messageTokens[0].compareTo("hit") == 0) {
				UUID targetID = UUID.fromString(messageTokens[1]);
				System.out.println("Received hit message for target: " + targetID);
				sendHitMessage(targetID);
			}

			// ^ ========================= Shield Stuff ========================= ^ //
			if (messageTokens[0].compareTo("shield_activate") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				sendShieldActivateMessages(clientID);
			}

			// Add processing for shield deactivate messages
			if (messageTokens[0].compareTo("shield_deactivate") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				sendShieldDeactivateMessages(clientID);
			}

			// Add processing for shield hit messages
			if (messageTokens[0].compareTo("shield_hit") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				sendShieldHitMessages(clientID);
			}

			// ^ ========================= Sword Stuff ========================= ^ //
			if (messageTokens[0].compareTo("sword_animate") == 0) {
				UUID clientID = UUID.fromString(messageTokens[1]);
				sendSwordAnimateMessages(clientID);
			}
		}
	}

	// Informs the client who just requested to join the server if their if their
	// request was able to be granted.
	// Message Format: (join,success) or (join,failure)

	public void sendJoinedMessage(UUID clientID, boolean success) {
		try {
			System.out.println("trying to confirm join");
			String message = new String("join,");
			if (success)
				message += "success";
			else
				message += "failure";
			sendPacket(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Informs a client that the avatar with the identifier remoteId has left the
	// server.
	// This message is meant to be sent to all client currently connected to the
	// server
	// when a client leaves the server.
	// Message Format: (bye,remoteId)

	public void sendByeMessages(UUID clientID) {
		try {
			String message = new String("bye," + clientID.toString());
			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Informs a client that a new avatar has joined the server with the unique
	// identifier
	// remoteId. This message is intended to be send to all clients currently
	// connected to
	// the server when a new client has joined the server and sent a create message
	// to the
	// server. This message also triggers WANTS_DETAILS messages to be sent to all
	// client
	// connected to the server.
	// Message Format: (create,remoteId,x,y,z) where x, y, and z represent the
	// position

	public void sendCreateMessages(UUID clientID, String[] position, String textureName) {
		try {
			String message = new String("create," + clientID.toString());
			message += "," + position[0];
			message += "," + position[1];
			message += "," + position[2];
			message += "," + textureName;
			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Informs a client of the details for a remote client�s avatar. This message is
	// in response
	// to the server receiving a DETAILS_FOR message from a remote client. That
	// remote client�s
	// message�s localId becomes the remoteId for this message, and the remote
	// client�s message�s
	// remoteId is used to send this message to the proper client.
	// Message Format: (dsfr,remoteId,x,y,z) where x, y, and z represent the
	// position.

	public void sendDetailsForMessage(UUID clientID, UUID remoteId, String[] position, String textureName) {
		try {
			String message = new String("dsfr," + remoteId.toString());
			message += "," + position[0];
			message += "," + position[1];
			message += "," + position[2];
			message += "," + textureName;
			sendPacket(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Informs a local client that a remote client wants the local client�s avatar�s
	// information.
	// This message is meant to be sent to all clients connected to the server when
	// a new client
	// joins the server.
	// Message Format: (wsds,remoteId)

	public void sendWantsDetailsMessages(UUID clientID) {
		try {
			String message = new String("wsds," + clientID.toString());
			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Informs a client that a remote client�s avatar has changed position. x, y,
	// and z represent
	// the new position of the remote avatar. This message is meant to be forwarded
	// to all clients
	// connected to the server when it receives a MOVE message from the remote
	// client.
	// Message Format: (move,remoteId,x,y,z) where x, y, and z represent the
	// position.

	public void sendMoveMessages(UUID clientID, String[] position) {
		try {
			String message = new String("move," + clientID.toString());
			message += "," + position[0];
			message += "," + position[1];
			message += "," + position[2];
			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendRotateMessages(UUID clientID, String[] rotMatrix) {
		try {
			String message = new String("rotate," + clientID.toString());
			// Add all 16 matrix values to the message
			for (int i = 0; i < 16; i++) {
				message += "," + rotMatrix[i];
			}
			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// ^ BALLS

	public void sendCreateBallMessages(UUID clientID, UUID ballID, String[] position) {
		try {
			String message = new String("createBall," + clientID.toString() + "," + ballID.toString());
			message += "," + position[0];
			message += "," + position[1];
			message += "," + position[2];

			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendMoveBallMessages(UUID clientID, UUID ballID, String[] position) {
		try {
			String message = new String("moveBall," + clientID.toString() + "," + ballID.toString());
			message += "," + position[0];
			message += "," + position[1];
			message += "," + position[2];

			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendRemoveBallMessages(UUID clientID, UUID ballID) {
		try {
			String message = new String("removeBall," + clientID.toString() + "," + ballID.toString());
			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendHitMessage(UUID targetID) {
		try {
			String message = new String("hit," + targetID.toString());
			sendPacket(message, targetID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// ^ ========================= Shield Stuff ========================= ^ //
	public void sendShieldActivateMessages(UUID clientID) {
		try {
			String message = new String("shield_activate," + clientID.toString());
			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendShieldDeactivateMessages(UUID clientID) {
		try {
			String message = new String("shield_deactivate," + clientID.toString());
			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendShieldHitMessages(UUID clientID) {
		try {
			String message = new String("shield_hit," + clientID.toString());
			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// ^ ========================= Sword Stuff ========================= ^ //
	public void sendSwordAnimateMessages(UUID clientID) {
		try {
			String message = new String("sword_animate," + clientID.toString());
			forwardPacketToAll(message, clientID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
