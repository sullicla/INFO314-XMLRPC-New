package edu.uw.info314.xmlrpc.server;

import static spark.Spark.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

class XMLRPCException extends Exception {
    private int faultCode;

    public int GetFaultCode() {
        return faultCode;
    }

    public XMLRPCException(int faultCode, String errorMessage) {
        super(errorMessage);
        this.faultCode = faultCode;
    }
}

class Call {
    private static final String operations[] = { "add", "subtract", "multiply", "divide", "modulo" };

    public String name;
    public List<Object> args = new ArrayList<Object>();

    public boolean hasValidArguments() {

        // false if An argument is not of type integer.
        for (Object obj : args) {
            if (!(obj instanceof Integer)) {
                return false;
            }
        }

        // Add or multiply
        if (name.equals(operations[0])
                || name.equals(operations[2])) {
            return true;
        }
        // Subtract, divide, or modulo
        else if (name.equals(operations[1])
                || name.equals(operations[3])
                || name.equals(operations[4])) {
            return args.size() == 2;
        }
        // False if unknown operation
        else {
            return false;
        }
    }

    public int getResult() throws XMLRPCException {
        int result = 0;

        // Add
        if (name.equals(operations[0])) {
            for (Object obj : args) {
                result += (Integer) obj;
            }
        }
        // Subtract
        else if (name.equals(operations[1])) {
            result = (Integer) args.get(0) - (Integer) args.get(1);
        }
        // Multiply
        else if (name.equals(operations[2])) {
            result = 1;
            for (Object obj : args) {
                result *= (Integer) obj;
            }
        }
        // Divide
        else if (name.equals(operations[3])) {
            if ((Integer) args.get(1) == 0) {
                throw new XMLRPCException(1, "Divide by zero");
            }

            result = (Integer) args.get(0) / (Integer) args.get(1);
        }
        // Modulo
        else if (name.equals(operations[4])) {
            if ((Integer) args.get(1) == 0) {
                throw new XMLRPCException(1, "Divide by zero");
            }

            result = (Integer) args.get(0) % (Integer) args.get(1);
        }

        return result;
    }
}

public class App {
    public static final Logger LOG = Logger.getLogger(App.class.getCanonicalName());
    private static DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

    public static void main(String[] args) {

        port(8080);
        LOG.info("Starting up on port 8080");

        // Handle RPC requests
        post("/RPC", (request, response) -> {
            String responseString = "";

            try {
                Call call = extractXMLRPCCall(request.body());

                // Valid, return result.
                if (call.hasValidArguments()) {
                    responseString = GetResponseSuccessBody(call.getResult());
                }
                // Bad arguments, return error message.
                else {
                    responseString = GetResponseFailBody(1, "Unexpected arugments for the requested method type.");
                }
            }
            // Exception occured while parsing the request or processing it
            catch (XMLRPCException e) {
                responseString = GetResponseFailBody(e.GetFaultCode(), e.getMessage());
            } catch (Exception e) {
                responseString = e.getMessage();
            }

            InetAddress addr;
            addr = InetAddress.getLocalHost();
            response.header("Host", addr.getHostName());
            response.status(200);
            return responseString;
        });

        // Return 405 on any other type of HTTP request
        post("/", (request, response) -> {
            response.status(404);
            return "Not found.";
        });

        get("/", (request, response) -> {
            response.status(405);
            return "Unsupported request.";
        });

        put("/", (request, response) -> {
            response.status(405);
            return "Unsupported request.";
        });

        delete("/", (request, response) -> {
            response.status(405);
            return "Unsupported request.";
        });

        options("/", (request, response) -> {
            response.status(405);
            return "Unsupported request.";
        });
    }

    public static Call extractXMLRPCCall(String xmlString) throws Exception {
        Call call = new Call();

        DocumentBuilder dBuilder = dbf.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(xmlString)));
        doc.getDocumentElement().normalize();

        // Assume there is only 1 method.
        Element method = (Element) doc.getElementsByTagName("methodCall").item(0);

        // Get method type.
        call.name = method.getElementsByTagName("methodName").item(0).getTextContent();

        // Get params.
        Element paramList = (Element) method.getElementsByTagName("params").item(0);
        NodeList params = paramList.getElementsByTagName("param");
        for (int i = 0; i < params.getLength(); ++i) {

            Element paramElement = (Element) params.item(i);
            Element valueElement = (Element) paramElement.getElementsByTagName("value").item(0);

            // Check that there is a child element with the i4 tag.
            NodeList typeElements = paramElement.getElementsByTagName("i4");
            if (typeElements.getLength() != 1) {
                throw new XMLRPCException(3,
                        "A param was requested that is not of type i4, this is not supported.");
            }

            call.args.add(Integer.parseInt(valueElement.getTextContent()));
        }

        return call;
    }

    private static String GetResponseSuccessBody(int result) throws Exception {
        DocumentBuilder dBuilder = dbf.newDocumentBuilder();
        Document doc = dBuilder.newDocument();

        Element rootElement = doc.createElement("methodResponse");
        doc.appendChild(rootElement);

        Element paramsElement = doc.createElement("params");
        rootElement.appendChild(paramsElement);

        Element paramElement = doc.createElement("param");
        paramsElement.appendChild(paramElement);

        Element valueElement = doc.createElement("value");
        paramElement.appendChild(valueElement);

        Element typeElement = doc.createElement("i4");
        typeElement.setTextContent("" + result);
        valueElement.appendChild(typeElement);

        return GetStringFromDoc(doc);
    }

    private static String GetResponseFailBody(int faultCode, String faultMessage) throws Exception {
        DocumentBuilder dBuilder = dbf.newDocumentBuilder();
        Document doc = dBuilder.newDocument();

        Element rootElement = doc.createElement("methodResponse");
        doc.appendChild(rootElement);

        Element faultElement = doc.createElement("fault");
        rootElement.appendChild(faultElement);

        Element faultValueElement = doc.createElement("value");
        faultElement.appendChild(faultValueElement);

        Element structElement = doc.createElement("struct");
        faultValueElement.appendChild(structElement);

        // ---------Fault Code----------------
        Element member1Element = doc.createElement("member");
        structElement.appendChild(member1Element);

        Element member1NameElement = doc.createElement("name");
        member1NameElement.setTextContent("faultCode");
        member1Element.appendChild(member1NameElement);

        Element member1ValueElement = doc.createElement("value");
        member1Element.appendChild(member1ValueElement);

        Element member1TypeElement = doc.createElement("i4");
        member1TypeElement.setTextContent("" + faultCode);
        member1ValueElement.appendChild(member1TypeElement);

        // ---------Fault Messsage----------------
        Element member2Element = doc.createElement("member");
        structElement.appendChild(member2Element);

        Element member2NameElement = doc.createElement("name");
        member2NameElement.setTextContent("faultString");
        member2Element.appendChild(member2NameElement);

        Element member2ValueElement = doc.createElement("value");
        member2Element.appendChild(member2ValueElement);

        Element member2TypeElement = doc.createElement("string");
        member2TypeElement.setTextContent("" + faultMessage);
        member2ValueElement.appendChild(member2TypeElement);

        return GetStringFromDoc(doc);
    }

    private static String GetStringFromDoc(Document doc) throws Exception {
        // Transform the document into a string.
        StringWriter sw = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
