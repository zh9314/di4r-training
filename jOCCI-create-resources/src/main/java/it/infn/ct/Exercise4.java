
/*
 *  Copyright 2016 EGI Foundation
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at

 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package it.infn.ct;

import java.io.File;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Properties;
import java.util.Set;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import java.net.URI;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URISyntaxException;

import cz.cesnet.cloud.occi.Model;
import cz.cesnet.cloud.occi.api.Client;
import cz.cesnet.cloud.occi.api.EntityBuilder;
import cz.cesnet.cloud.occi.api.exception.CommunicationException;
import cz.cesnet.cloud.occi.api.exception.EntityBuildingException;
import cz.cesnet.cloud.occi.api.http.HTTPClient;
import cz.cesnet.cloud.occi.api.http.auth.HTTPAuthentication;
import cz.cesnet.cloud.occi.api.http.auth.VOMSAuthentication;
import cz.cesnet.cloud.occi.core.Action;
import cz.cesnet.cloud.occi.core.ActionInstance;
import cz.cesnet.cloud.occi.core.Attribute;
import cz.cesnet.cloud.occi.core.Entity;
import cz.cesnet.cloud.occi.core.Mixin;
import cz.cesnet.cloud.occi.core.Resource;
import cz.cesnet.cloud.occi.core.Kind;
import cz.cesnet.cloud.occi.exception.AmbiguousIdentifierException;
import cz.cesnet.cloud.occi.exception.InvalidAttributeValueException;
import cz.cesnet.cloud.occi.exception.RenderingException;
import cz.cesnet.cloud.occi.infrastructure.Compute;
import cz.cesnet.cloud.occi.parser.MediaType;
import cz.cesnet.cloud.occi.core.Link;
import cz.cesnet.cloud.occi.infrastructure.NetworkInterface;
import cz.cesnet.cloud.occi.infrastructure.IPNetworkInterface;
import cz.cesnet.cloud.occi.infrastructure.Storage;

import org.apache.commons.codec.binary.Base64;

public class Exercise4
{
    // Create cloud resources in the selected cloud provider.
    // Available resources that can be created via API are the following:
    // - compute = computing resource, 
    // - storage = storage resources.

    // [ Setting preferences here! ]
    public static String ACTION = "create";
    public static String AUTH = "x509";
    public static String TRUSTED_CERT_REPOSITORY_PATH = "/etc/grid-security/certificates";
    public static String PROXY_PATH = "/tmp/x509up_u1000"; // <= Change here!

    public static String OCCI_ENDPOINT_HOST = "https://carach5.ics.muni.cz:11443"; // <= Change here!

    // *Create* a new virtual appliance (aka VM) with contextualization (public_key)
    public static List<String> RESOURCE = Arrays.asList("compute"); // <= Change here! (e.g.: compute or storage)

    public static List<String> MIXIN = 
    Arrays.asList("resource_tpl#medium", // <= Change here!
    "http://occi.carach5.ics.muni.cz/occi/infrastructure/os_tpl#uuid_training_jupyter_notebook_centos_6_fedcloud_warg_162"); 
	// <= Change here!

    public static List<String> CONTEXT = 
    	Arrays.asList("public_key=file:/home/ubuntu/.ssh/id_rsa.pub", // <= Change here!
    	"user_data=file:/home/userX/di4r-training/jOCCI-create-resources/contextualization.txt"); // <= Change here!

    public static List<String> ATTRIBUTES = Arrays.asList("occi.core.title=VM_title"); // <= Change here!
    
    public static String OCCI_PUBLICKEY_NAME = "centos";



    // *Create* a new storage volume
    //public static List<String> ATTRIBUTES = 
	//Arrays.asList("occi.core.title=VM_volume_1", "occi.storage.size=1"); // <= Change here!
    
    public static Boolean verbose = false;


    // Creating a new VM in the OCCI_ENDPOINT_HOST cloud resource
    public static void doCreate (Properties properties, EntityBuilder eb, Model model, Client client)
    {
	URI uri_location = null;
        String networkInterfaceLocation = "";
        String networkInterfaceLocation_stripped = "";
	Resource vm_resource = null;

 	System.out.println();

	try {

		if (properties.getProperty("RESOURCE").equals("compute")) {

			String segments[] = properties.getProperty("OCCI_OS_TPL").split("#");
			String OCCI_OS_TPL = segments[segments.length - 1];

			String segments2[] = properties.getProperty("OCCI_RESOURCE_TPL").split("#");
			String OCCI_RESOURCE_TPL = segments2[segments2.length - 1];

			System.out.println("[+] Creating a new compute Virtual Machine (VM)");

			// Creating a compute instance
			Resource compute = eb.getResource("compute");
                	Mixin mixin = model.findMixin(OCCI_OS_TPL);
	                compute.addMixin(mixin);
        	        compute.addMixin(model.findMixin(OCCI_OS_TPL, "os_tpl"));
                	compute.addMixin(model.findMixin(OCCI_RESOURCE_TPL, "resource_tpl"));
	                
			// Checking the context
			if (properties.getProperty("PUBLIC_KEY_FILE") != null && 
                           !properties.getProperty("PUBLIC_KEY_FILE").isEmpty()) 
			{				
				String _public_key_file = 
                                properties.getProperty("PUBLIC_KEY_FILE")
                                .substring(properties.getProperty("PUBLIC_KEY_FILE").lastIndexOf(":") + 1);

				File f = new File(_public_key_file);
	                        FileInputStream fis = new FileInputStream(f);
        	                DataInputStream dis = new DataInputStream(fis);
                	        byte[] keyBytes = new byte[(int) f.length()];
                        	dis.readFully(keyBytes);
	                        dis.close();
        	                String _publicKey = new String (keyBytes).trim();

				// Add SSH public key
	        	        compute
				.addMixin(model.findMixin(URI.create("http://schemas.openstack.org/instance/credentials#public_key")));

		                compute
				.addAttribute("org.openstack.credentials.publickey.data", _publicKey);
		
				// Add the name for the public key	
				if (OCCI_PUBLICKEY_NAME != null && !OCCI_PUBLICKEY_NAME.isEmpty()) 
		                	compute.addAttribute("org.openstack.credentials.publickey.name",
						properties.getProperty("OCCI_PUBLICKEY_NAME"));
			} 

			if (properties.getProperty("USER_DATA") != null && 
                           !properties.getProperty("USER_DATA").isEmpty()) 
                        {
				String _user_data =
                                properties.getProperty("USER_DATA")
                                .substring(properties.getProperty("USER_DATA").lastIndexOf(":") + 1);

                                File f = new File(_user_data);
                                FileInputStream fis = new FileInputStream(f);
                                DataInputStream dis = new DataInputStream(fis);
                                byte[] keyBytes = new byte[(int) f.length()];
                                dis.readFully(keyBytes);
                                dis.close();
				byte[] data = Base64.encodeBase64(keyBytes);
                                String user_data = new String (data);

				compute
                                .addMixin(model.findMixin(URI.create("http://schemas.openstack.org/compute/instance#user_data")));
		
				compute.addAttribute("org.openstack.compute.user_data", user_data);
			}

        	        // Set VM title
			compute.setTitle(properties.getProperty("OCCI_CORE_TITLE"));

			//System.out.println(mixin.toText());
        	        URI location = client.create(compute);
			System.out.println("URI = " + location);

		} // end 'compute'
		
		if (properties.getProperty("RESOURCE").equals("storage")) {
 			System.out.println("[+] Creating a volume storage");
                        // Creating a storage instance
			Storage storage = eb.getStorage();
 			storage.setTitle(properties.getProperty("OCCI_CORE_TITLE"));
			storage.setSize(properties.getProperty("OCCI_STORAGE_SIZE"));
		       	
			URI storageLocation = client.create(storage);
			
			List<URI> list = client.list("storage");
			List<URI> storageURIs = new ArrayList<URI>();
			//uri_location = list.get(0);
			for (URI uri : list) {
				if (uri.toString().contains("storage")) 
					storageURIs.add(uri);
			}
					
			System.out.println("URI = " + storageLocation);
		} // end 'storage'

	} catch (FileNotFoundException ex) {
		throw new RuntimeException(ex);
	} catch (IOException ex) {
		throw new RuntimeException(ex);
	} catch (EntityBuildingException | AmbiguousIdentifierException |
		 InvalidAttributeValueException | CommunicationException ex) {
		throw new RuntimeException(ex);
	}
    }

    public static void main (String[] args)
    {
        Boolean result = false;
        String networkInterfaceLocation = "";
        String networkInterfaceLocation_stripped = "";
        Resource vm_resource = null;
	URI uri_location = null;
        
	if (verbose) {
		System.out.println();
		if (ACTION != null && !ACTION.isEmpty()) 
			System.out.println("[ACTION] = " + ACTION);
		else	
			System.out.println("[ACTION] = Get dump model");
		System.out.println("AUTH = " + AUTH);
		//System.out.println("VOMS = " + VOMS);
		if (OCCI_ENDPOINT_HOST != null && !OCCI_ENDPOINT_HOST.isEmpty()) 
			System.out.println("OCCI_ENDPOINT_HOST = " + OCCI_ENDPOINT_HOST);
		if (RESOURCE != null && !RESOURCE.isEmpty()) 
			System.out.println("RESOURCE = " + RESOURCE);
		if (MIXIN != null && !MIXIN.isEmpty()) 
			System.out.println("MIXIN = " + MIXIN);
		if (TRUSTED_CERT_REPOSITORY_PATH != null && !TRUSTED_CERT_REPOSITORY_PATH.isEmpty()) 
			System.out.println("TRUSTED_CERT_REPOSITORY_PATH = " + TRUSTED_CERT_REPOSITORY_PATH);
		if (PROXY_PATH != null && !PROXY_PATH.isEmpty()) 
			System.out.println("PROXY_PATH = " + PROXY_PATH);
		if (CONTEXT != null && !CONTEXT.isEmpty()) 
			System.out.println("CONTEXT = " + CONTEXT);
		if (OCCI_PUBLICKEY_NAME != null && !OCCI_PUBLICKEY_NAME.isEmpty()) 
			System.out.println("OCCI_PUBLICKEY_NAME = " + OCCI_PUBLICKEY_NAME);
		if (ATTRIBUTES != null && !ATTRIBUTES.isEmpty()) 
			System.out.println("ATTRIBUTES = " + ATTRIBUTES);
		if (verbose) System.out.println("Verbose = True ");
		else System.out.println("Verbose = False ");
	}

        Properties properties = new Properties();
	if (ACTION != null && !ACTION.isEmpty())
	        properties.setProperty("ACTION", ACTION);
	if (OCCI_ENDPOINT_HOST != null && !OCCI_ENDPOINT_HOST.isEmpty())
	        properties.setProperty("OCCI_ENDPOINT_HOST", OCCI_ENDPOINT_HOST);

	if (RESOURCE != null && !RESOURCE.isEmpty()) 
        for (int i=0; i<RESOURCE.size(); i++) {
	        if ( (!RESOURCE.get(i).equals("compute")) && 
	             (!RESOURCE.get(i).equals("storage")) &&
	             (!RESOURCE.get(i).equals("network")) &&
	             (!RESOURCE.get(i).equals("os_tpl")) &&
	             (!RESOURCE.get(i).equals("resource_tpl")) )
        		properties.setProperty("OCCI_VM_RESOURCE_ID", RESOURCE.get(i));
        		
		else { 
			properties.setProperty("RESOURCE", RESOURCE.get(i));
        		properties.setProperty("OCCI_VM_RESOURCE_ID", "empty");
		}
        }
			
	if (MIXIN != null && !MIXIN.isEmpty()) 
        for (int i=0; i<MIXIN.size(); i++) 
	{
	        if (MIXIN.get(i).contains("template") || 
		    MIXIN.get(i).contains("os_tpl")) 
	        	properties.setProperty("OCCI_OS_TPL", MIXIN.get(i));

	        if (MIXIN.get(i).contains("resource_tpl")) 
	        	properties.setProperty("OCCI_RESOURCE_TPL", MIXIN.get(i));
	}
		
        if (ATTRIBUTES != null && !ATTRIBUTES.isEmpty())
        for (int i=0; i<ATTRIBUTES.size(); i++) 
	{
                if (ATTRIBUTES.get(i).contains("occi.core.title")) {
			String _OCCI_CORE_TITLE = ATTRIBUTES.get(i)
                             .substring(ATTRIBUTES.get(i).lastIndexOf("=") + 1);

                        properties.setProperty("OCCI_CORE_TITLE", _OCCI_CORE_TITLE);
		}
                if (ATTRIBUTES.get(i).contains("occi.storage.size")) {
			String _OCCI_STORAGE_SIZE = ATTRIBUTES.get(i)
                             .substring(ATTRIBUTES.get(i).lastIndexOf("=") + 1);

                        properties.setProperty("OCCI_STORAGE_SIZE", _OCCI_STORAGE_SIZE);
		}
        }

        properties.setProperty("TRUSTED_CERT_REPOSITORY_PATH", TRUSTED_CERT_REPOSITORY_PATH);
        properties.setProperty("PROXY_PATH", PROXY_PATH);

	if (CONTEXT != null && !CONTEXT.isEmpty()) {
        	for (int i=0; i<CONTEXT.size(); i++) {
			if (CONTEXT.get(i).contains("public_key")) 
				properties.setProperty("PUBLIC_KEY_FILE", CONTEXT.get(i));

			if (CONTEXT.get(i).contains("user_data")) 
				properties.setProperty("USER_DATA", CONTEXT.get(i));
        		//properties.setProperty("PUBLIC_KEY_FILE", PUBLIC_KEY_FILE);
        	}
	}

	if (OCCI_PUBLICKEY_NAME != null && !OCCI_PUBLICKEY_NAME.isEmpty())
	        properties.setProperty("OCCI_PUBLICKEY_NAME", OCCI_PUBLICKEY_NAME);
        properties.setProperty("OCCI_AUTH", AUTH);

	try {
		HTTPAuthentication authentication = new VOMSAuthentication(PROXY_PATH);
		
		authentication.setCAPath(TRUSTED_CERT_REPOSITORY_PATH);
	        
		Client client = new HTTPClient(URI.create(OCCI_ENDPOINT_HOST),
                                authentication, MediaType.TEXT_PLAIN, false);

            	//Connect client
            	client.connect();
		
		Model model = client.getModel();
                EntityBuilder eb = new EntityBuilder(model);

		doCreate(properties, eb, model, client);		

	} catch (CommunicationException ex ) {
                 throw new RuntimeException(ex);
        }
    }
}
