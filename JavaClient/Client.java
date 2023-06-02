import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import org.w3c.dom.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.*;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.OutputKeys;
import javax.xml.xpath.*;

/**
 * This approach uses the java.net.http.HttpClient classes, which
 * were introduced in Java11.
 */
public class Client {
    private static DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    private static String urlString = "";

    public static void main(String... args) throws Exception {

        if (args.length != 2) {
            System.out.println("Incorrect arguments, you must enter the address of the endpoint then the port");
            return;
        }

        urlString = "http://" + args[0] + ":" + args[1] + "/RPC";

        System.out.println(add() == 0);
        System.out.println(add(1, 2, 3, 4, 5) == 15);
        System.out.println(add(2, 4) == 6);
        System.out.println(subtract(12, 6) == 6);
        System.out.println(multiply(3, 4) == 12);
        System.out.println(multiply(1, 2, 3, 4, 5) == 120);
        System.out.println(divide(10, 5) == 2);
        System.out.println(modulo(10, 5) == 0);
    }

    public static int add(Integer... params) throws Exception {
        String xmlString = GetXML("add", params);
        return SendCalculationRequest(xmlString);
    }

    public static int subtract(int lhs, int rhs) throws Exception {
        String xmlString = GetXML("subtract", lhs, rhs);
        return SendCalculationRequest(xmlString);
    }

    public static int multiply(Integer... params) throws Exception {
        String xmlString = GetXML("multiply", params);
        return SendCalculationRequest(xmlString);
    }

    public static int divide(int lhs, int rhs) throws Exception {
        String xmlString = GetXML("divide", lhs, rhs);
        return SendCalculationRequest(xmlString);
    }

    public static int modulo(int lhs, int rhs) throws Exception {
        String xmlString = GetXML("modulo", lhs, rhs);
        return SendCalculationRequest(xmlString);
    }

    private static String GetXML(String operation, Integer... params) throws Exception {

        // Build document following the method request format.
        DocumentBuilder dBuilder = dbf.newDocumentBuilder();
        Document doc = dBuilder.newDocument();

        Element rootElement = doc.createElement("methodCall");
        doc.appendChild(rootElement);

        Element methodNameElement = doc.createElement("methodName");
        methodNameElement.setTextContent(operation);
        rootElement.appendChild(methodNameElement);

        Element paramsListelement = doc.createElement("params");
        rootElement.appendChild(paramsListelement);

        for (Integer paramValue : params) {
            Element paramElement = doc.createElement("param");
            paramsListelement.appendChild(paramElement);

            Element paramValueElement = doc.createElement("value");
            paramElement.appendChild(paramValueElement);

            Element paramTypeElement = doc.createElement("i4");
            paramTypeElement.setTextContent(paramValue.toString());
            paramValueElement.appendChild(paramTypeElement);
        }

        return GetStringFromDoc(doc);
    }

    private static int SendCalculationRequest(String xml) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(xml))
                .header("Content-Type", "text/xml")
                .uri(URI.create(urlString))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // If not 200, something went wrong when processing the request on the server,
        // throw a exception with info from server.
        if (response.statusCode() != 200) {
            throw new Exception("Responded with status :" + response.statusCode() +
                    ": " + response.body());
        }

        return GetResponseFromXML(response.body());
    }

    private static int GetResponseFromXML(String xmlString) throws Exception {
        DocumentBuilder dBuilder = dbf.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(xmlString)));
        doc.getDocumentElement().normalize();

        // Assume there is only 1 method.
        Element response = (Element) doc.getElementsByTagName("methodResponse").item(0);
        NodeList fault = doc.getElementsByTagName("fault");

        // Handle fail
        if (fault != null && fault.getLength() > 0) {
            Element faultElement = (Element) fault.item(0);
            Element value = (Element) faultElement.getElementsByTagName("value").item(0);
            Element struct = (Element) value.getElementsByTagName("struct").item(0);

            NodeList members = struct.getElementsByTagName("member");
            String faultCode = "";
            String faultMessage = "";
            for (int i = 0; i < members.getLength(); ++i) {
                Element memberElement = (Element) members.item(i);
                Element memeberNameElement = (Element) memberElement.getElementsByTagName("name").item(0);
                String memberName = memeberNameElement.getTextContent();
                Element memberValue = (Element) memberElement.getElementsByTagName("value").item(0);
                if (memberName.equals("faultCode")) {
                    faultCode = memberValue.getTextContent();
                } else if (memberName.equals("faultString")) {
                    faultMessage = memberValue.getTextContent();
                }
            }

            throw new Exception("Server could not handle request. Fault code:" + faultCode + ". " + faultMessage);
        }
        // Handle Success
        else {
            // Assume just one param.
            Element responseParam = (Element) response.getElementsByTagName("param").item(0);
            Element value = (Element) responseParam.getElementsByTagName("value").item(0);
            NodeList type = value.getElementsByTagName("i4");
            if (type == null || type.getLength() != 1) {
                // Returned wrong type
                throw new Exception("Server responded with incorrect type.");
            }

            Element typeElement = (Element) type.item(0);
            return Integer.parseInt(typeElement.getTextContent());
        }
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
