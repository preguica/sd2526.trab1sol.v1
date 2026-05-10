package sd2526.trab.impl.grpc.servers;


import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.util.List;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import sd2526.trab.impl.discovery.Discovery;
import sd2526.trab.impl.java.servers.AbstractServer;
import sd2526.trab.impl.utils.IP;


public abstract class AbstractGrpcServer extends AbstractServer {
	private static final String SERVER_BASE_URI = "grpc://%s:%s%s";

	private static final String GRPC_CTX = "/grpc";

	protected Server server;

	protected AbstractGrpcServer(Logger log, String service, int port) {
		super(log, service, String.format(SERVER_BASE_URI, IP.hostname(), port, GRPC_CTX));
	}

	protected abstract List<GrpcController> controllers( String uri );
	
	protected void start() throws IOException {
		/*
		We will start by accessing the values of the default
		JVM properties (we will see how we configure these
		later) regarding both the filename that contains the
		server keystore and the password that protects that
		keystore.
		*/
		String keyStoreFilename = System.getProperty("javax.net.ssl.keyStore");
		String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");
		
		/*
		Then we obtain an instance of a keystore (initially
		empty) and load it with the cryptographic information
		stored in the server keystore. To do that we open a
		FileInputStream to the keystore file and load its
		contents providing the key that protects the file.
		*/
		KeyStore keystore = null;
		try {
			keystore = KeyStore.getInstance(KeyStore.getDefaultType());
			try(FileInputStream input = new FileInputStream(keyStoreFilename)) {
				try {
					keystore.load(input, keyStorePassword.toCharArray());
				} catch (Exception e) {
					throw new IllegalStateException("Failed to load keystore", e);
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load default keystore", e);
		}

		/*
		Next, we need a KeyManagerFactory that can be
		generated using its factory and using the default
		cryptographic algorithms.
		We then load into this factory the keystore that we
		have previously prepared (again providing the
		password that protects that keystore).
		*/
		KeyManagerFactory keyManagerFactory = null;
		try {
			keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagerFactory.init(keystore, keyStorePassword.toCharArray());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load default KeyManagerFactory", e);
		}

		/*
		We can now initialize an SslContext that will leverage
		the KeyManagerFactory to be able to access the
		private key and public key certificate of the server.
		*/
		SslContext context = GrpcSslContexts.configure(SslContextBuilder.forServer(keyManagerFactory)).build();

		/*
		We can now use the NettyServerBuilder (instead of
		the Grpc class as we did in the first project) to create
		an instance of Server, providing the port of the server,
		the instance of the gRPC server stub, and the SSL
		Context that we have prepared.
		*/
		var builder = NettyServerBuilder.forPort(URI.create(serverURI).getPort());
		for( var s : controllers( super.serverURI ) )
			builder.addService( s );
		builder.sslContext(context);
		this.server = builder.build();
		
		Discovery.getInstance().announce(serviceName(), super.serverURI);
		
		Log.info(String.format("%s gRPC Server ready @ %s\n", service, serverURI));

		this.server.start();
		Runtime.getRuntime().addShutdownHook(new Thread( () -> {
			System.err.println("*** shutting down gRPC server since JVM is shutting down");
			this.server.shutdownNow();
			System.err.println("*** server shut down");
		}));
	}
	
}
