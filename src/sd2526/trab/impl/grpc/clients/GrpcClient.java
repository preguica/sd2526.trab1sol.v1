package sd2526.trab.impl.grpc.clients;

import static sd2526.trab.api.java.Result.error;
import static sd2526.trab.api.java.Result.ok;
import static sd2526.trab.api.java.Result.ErrorCode.INTERNAL_ERROR;

import java.io.FileInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.function.Supplier;

import javax.net.ssl.TrustManagerFactory;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;

public class GrpcClient {

	final protected URI serverURI;
	final protected Channel channel;
	
	protected GrpcClient(String serverUrl) {
		this.serverURI = URI.create(serverUrl);

		/*
		We will start by accessing the values of the default JVM
		properties (that has shown before should be passed as
		an argument when starting the process) regarding both
		the filename that contains the truststore and the
		password that protects that truststore.
		*/
		String trustStoreFilename = System.getProperty("javax.net.ssl.trustStore");
		String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");

		/*
		Similar to the server side, we will have to create a
		(initially empty) truststore –
		notice that a truststore is
		just a keystore, the main difference is that it only stores
		certificates with public keys of entities that we trust –
		that we will load with the content of the file containing
		the truststore, also providing the password that protects
		that file.
		*/
		KeyStore trustStore = null;
		try {
			trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
			try(FileInputStream input = new FileInputStream(trustStoreFilename)) {
				trustStore.load(input, trustStorePassword.toCharArray());
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load default truststore", e);
		}

		/*
		Instead of a KeyManagerFactory, for the client side we
		instead need a TrustManagerFactory, that we can
		instanciate using its factory and using default
		cryptographic algorithms.
		We then have to initialize this trustManagerFactory with
		the information that we loaded to our truststore.
 		*/
		TrustManagerFactory trustManagerFactory = null;
		try {
			trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(trustStore);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load default TrustManagerFactory", e);
		}
		
		/*
		We can now generate a SslContext for clients providing
		to it the trustManagerFactory that we have just created
		and initialized.
		*/
		SslContext context = null;
		try {
			context = GrpcSslContexts.configure(SslContextBuilder.forClient().trustManager(trustManagerFactory)).build();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load default SslContext", e);
		}

		/*
		And now we can create the communication channel that
		we will be using the interact with the remote gRPC
		server. Notice that we are now using the
		NettyChannelBuilder (instead of the
		ManagedChannelBuilder as we were doing before).
		The options are the same (including the enableRetry
		option) but now we must provide also the SSL Context
		that contains the certificates with the public keys of
		entities that we trust.
		*/
		this.channel = NettyChannelBuilder.forAddress(serverURI.getHost(), serverURI.getPort())
				.sslContext(context)
				.enableRetry()
				.build();
	}
	
	protected <T> Result<T> toJavaResult(Supplier<T> func) {
		try {
			return ok(func.get());
		} catch (StatusRuntimeException sre) {
			//sre.printStackTrace();
			return error(statusToErrorCode(sre.getStatus()));
		} catch (Exception x) {
			x.printStackTrace();
			return Result.error(INTERNAL_ERROR);
		}
	}
	
	protected Result<Void> toJavaResult(Runnable proc) {
		return toJavaResult( () -> {
			proc.run();
			return null;
		} );		
	}

	protected static ErrorCode statusToErrorCode(Status status) {
		return switch (status.getCode()) {
		case OK -> ErrorCode.OK;
		case NOT_FOUND -> ErrorCode.NOT_FOUND;
		case ALREADY_EXISTS -> ErrorCode.CONFLICT;
		case PERMISSION_DENIED -> ErrorCode.FORBIDDEN;
		case INVALID_ARGUMENT -> ErrorCode.BAD_REQUEST;
		case UNIMPLEMENTED -> ErrorCode.NOT_IMPLEMENTED;
		default -> ErrorCode.INTERNAL_ERROR;
		};
	}
	
	@Override
	public String toString() {
		return serverURI.toString();
	}
}

