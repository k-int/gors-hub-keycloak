package com.k_int.sierra.keycloak.provider.external;


import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;


@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SierraClient {

	@GET
	@Path("/{id}")
	SierraUser getSierraUserById(@PathParam("id") String id);

        /**
         * II: Don't think we need to expose this in keycloak so not adding the ws annotations
         */
        public SierraUser getSierraUserByUsername(String id);
        public SierraUser getSierraUserByBarcode(String id);


        // Check the pin to see if it's correct
        /**
         *
         */
	public boolean isValid(String barcode, String pin) throws java.io.UnsupportedEncodingException, java.io.IOException;

}
