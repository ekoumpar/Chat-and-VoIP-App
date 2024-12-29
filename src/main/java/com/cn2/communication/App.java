package com.cn2.communication;

import java.io.*;
import java.net.*;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.awt.event.*;
import java.awt.Color;
import java.lang.Thread;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class App extends Frame implements WindowListener, ActionListener {

	// Definition of the app's fields
	static TextField inputTextField;
	static JTextArea textArea;
	static JFrame frame;
	static JButton sendButton;
	static JTextField meesageTextField;
	public static Color gray;
	final static String newline = "\n";
	static JButton callButton;

	// Networking Variables
	public DatagramSocket textSocket;
	public DatagramSocket voiceSocket;
	private TargetDataLine voiceLine;
	private SourceDataLine speakerLine;
	public InetAddress remoteAddress;
	public int textLocalPort = 12345;
	public int textRemotePort = 12346;
	public int voiceLocalPort = 12350;
	public int voiceRemotePort = 12351;
	public String IPAddress;
	public volatile boolean isCalling = false; // whether a call is in progress

	// Construct the app's frame and initialize important parameters
	public App(String title) { // Defining the components of the GUI

		// Setting up the characteristics of the frame
		super(title);
		gray = new Color(245, 245, 245);
		setBackground(gray);
		setLayout(new FlowLayout());
		addWindowListener(this);

		// Setting up the TextField and the TextArea
		inputTextField = new TextField();
		inputTextField.setColumns(20);

		// Setting up the TextArea.
		textArea = new JTextArea(10, 40);
		textArea.setLineWrap(true);
		textArea.setEditable(false);
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		// Setting up the buttons
		sendButton = new JButton("Send");
		callButton = new JButton("Call");
		sendButton.setBackground(new Color(70, 130, 180));
		sendButton.setForeground(Color.WHITE);
		callButton.setBackground(new Color(70, 130, 180));
		callButton.setForeground(Color.WHITE);

		// Adding the components to the GUI
		add(scrollPane);
		add(inputTextField);
		add(sendButton);
		add(callButton);

		// Linking the buttons to the ActionListener
		sendButton.addActionListener(this);
		callButton.addActionListener(this);

	}

	public static void main(String[] args) {

		// Create the app's window
		App app = new App("CN2 - AUTH");
		app.setSize(500, 250);
		app.setVisible(true);

		// Enable communication
		try {
			app.IPAddress = new String("192.168.1.150");
			app.remoteAddress = InetAddress.getByName(app.IPAddress);
			app.voiceSocket = new DatagramSocket(app.voiceLocalPort);
			app.textSocket = new DatagramSocket(app.textLocalPort);

		} catch (Exception e) {
			e.printStackTrace();
			textArea.append("Error opening Socket: " + e.getMessage() + "\n");
		}

		// Receive text messages
		new Thread(() -> app.receiveMessages()).start();
	}

	@Override
	public void actionPerformed(ActionEvent e) { // Check which button was clicked

		if (e.getSource() == sendButton) { // "Send" button clicked
			sendMessages();
		} else if (e.getSource() == callButton) { // "Call" button clicked
			handleCallButton();
		}
	}

	public void receiveMessages() {

		while (true) {
			try {
				// Receive message
				byte[] buffer = new byte[1024];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				textSocket.receive(packet);
				String receiveMess = new String(packet.getData(), 0, packet.getLength());

				// Show message
				textArea.append("Remote: " + receiveMess + "\n");
			} catch (IOException ex) {
				if (!textSocket.isClosed()) { // Error when socket is active
					ex.printStackTrace();
					textArea.append("Error receiving message: " + ex.getMessage() + "\n");
				}
			}
		}
	}

	public void sendMessages() {
		String sendMessage = inputTextField.getText();
		if (sendMessage.isEmpty() || remoteAddress == null) {
			textArea.append("Error: No remote address or message is empty.\n");
			return;
		}
		try {
			// send message
			byte[] buffer = new byte[1024];
			buffer = sendMessage.getBytes();
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length, remoteAddress, textRemotePort);
			textSocket.send(packet);

			// show message in sender's window
			textArea.append("Local: " + sendMessage + "\n");
			inputTextField.setText("");

		} catch (IOException ex) {
			ex.printStackTrace();
			textArea.append("Error sending message: " + ex.getMessage() + "\n");

		}

	}

	public void handleCallButton() {

		if (!isCalling) { // if no call in progress

			isCalling = true;

			callButton.setBackground(Color.GREEN);
			callButton.setText("End Call");

			// start calling
			new Thread(this::call).start();

		} else { // if call is in progress

			// end Call
			textArea.append("Call Ended.\n");
			endCall();

			callButton.setBackground(new Color(70, 130, 180));
			callButton.setText("Call");

		}

	}

	public void call() {
		Thread sendVoiceThread = null;

		try {
			AudioFormat format = new AudioFormat(8000, 8, 1, true, true);

			// Microphone setup
			DataLine.Info voiceInfo = new DataLine.Info(TargetDataLine.class, format);
			voiceLine = (TargetDataLine) AudioSystem.getLine(voiceInfo);
			voiceLine.open(format);
			voiceLine.start();

			// Speaker setup
			DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
			speakerLine = (SourceDataLine) AudioSystem.getLine(speakerInfo);
			speakerLine.open(format);
			speakerLine.start();

			textArea.append("Start calling..." + "\n");

			// Thread for recording and sending voice packets
			sendVoiceThread = new Thread(() -> {
				while (isCalling) {
					try {
						
						byte[] buffer = new byte[1024];
						int audioMessage = voiceLine.read(buffer, 0, buffer.length);
						DatagramPacket voiceSend = new DatagramPacket(buffer, audioMessage, remoteAddress,
								voiceRemotePort);
						voiceSocket.send(voiceSend);

					} catch (IOException ex) {
						if (!voiceSocket.isClosed()) { // Error only when socket is active
							ex.printStackTrace();
							textArea.append("Error in sending voice: " + ex.getMessage() + "\n");
						}
					}
				}
			});
			sendVoiceThread.start();

			// Receiving and listening to voice packets
			while (isCalling) {
				try {

					byte[] buffer = new byte[1024];
					DatagramPacket voiceReceive = new DatagramPacket(buffer, buffer.length);
					voiceSocket.receive(voiceReceive);
					speakerLine.write(voiceReceive.getData(), 0, voiceReceive.getLength());

				} catch (IOException ex) {
					if (!voiceSocket.isClosed()) { // Error only when socket is active
						ex.printStackTrace();
						textArea.append("Error in receiving voice: " + ex.getMessage() + "\n");
					}

				}
			}

		} catch (LineUnavailableException ex) {
			ex.printStackTrace();
			textArea.append("Error in audio setup or communication: " + ex.getMessage() + "\n");

		} finally {
			endCall();
		}
	}

	public void endCall() { // free resources

		isCalling = false;

		if (voiceLine != null && voiceLine.isOpen()) {
			voiceLine.stop();
			voiceLine.close();
		}
		if (speakerLine != null && speakerLine.isOpen()) {
			speakerLine.stop();
			speakerLine.close();
		}

	}

	@Override
	public void windowActivated(WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowClosed(WindowEvent e) {

	}

	@Override
	public void windowClosing(WindowEvent e) {

		if (textSocket != null && !textSocket.isClosed()) {
			textSocket.close();
		}
		if (voiceSocket != null && !voiceSocket.isClosed()) {
			voiceSocket.close();
		}
		dispose();
		System.exit(0);
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowIconified(WindowEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void windowOpened(WindowEvent e) {
		// TODO Auto-generated method stub
	}

}
