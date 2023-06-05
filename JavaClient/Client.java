import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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
        String xmlString = getXML("add", params);
        return sendCalculationRequest(xmlString);
    }

    public static int subtract(int lhs, int rhs) throws Exception {
        String xmlString = getXML("subtract", lhs, rhs);
        return sendCalculationRequest(xmlString);
    }

    public static int multiply(Integer... params) throws Exception {
        String xmlString = getXML("multiply", params);
        return sendCalculationRequest(xmlString);
    }

    public static int divide(int lhs, int rhs) throws Exception {
        String xmlString = getXML("divide", lhs, rhs);
        return sendCalculationRequest(xmlString);
    }

    public static int modulo(int lhs, int rhs) throws Exception {
        String xmlString = getXML("modulo", lhs, rhs);
        return sendCalculationRequest(xmlString);
    }

    private static String getXML(String operation, Integer... params) throws Exception {
        DocumentBuilder dBuilder = dbf.newDocumentBuilder();
        Document doc = dBuilder.newDocument();

        Element rootElement = doc.createElement("methodCall");
        doc.appendChild(rootElement);

        Element methodNameElement = doc.createElement("methodName");
        methodNameElement.setTextContent(operation);
        rootElement.appendChild(methodNameElement);

        Element paramsListElement = doc.createElement("params");
        rootElement.appendChild(paramsListElement);

        for (Integer paramValue : params) {
            Element paramElement = doc.createElement("param");
            paramsListElement.appendChild(paramElement);

            Element paramValueElement = doc.createElement("value");
            paramElement.appendChild(paramValueElement);

            Element paramTypeElement = doc.createElement("i4");
            paramTypeElement.setTextContent(paramValue.toString());
            paramValueElement.appendChild(paramTypeElement);
        }

        return getStringFromDoc(doc);
    }

    private static int sendCalculationRequest(String xml) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(xml))
                .header("Content-Type", "text/xml")
                .uri(URI.create(urlString))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Responded with status: " + response.statusCode() + ": " + response.body());
        }

        return getResponseFromXML(response.body());
    }

    private static int getResponseFromXML(String xmlString) throws Exception {
        DocumentBuilder dBuilder = dbf.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(xmlString)));
        doc.getDocumentElement().normalize();

        Element response = (Element) doc.getElementsByTagName("methodResponse").item(0);
        NodeList fault = doc.getElementsByTagName("fault");

        if (fault != null && fault.getLength() > 0) {
            Element faultElement = (Element) fault.item(0);
            Element value = (Element) faultElement.getElementsByTagName("value").item(0);
            Element struct = (Element) value.getElementsByTagName("struct").item(0);

            NodeList members = struct.getElementsByTagName("member");
            String faultCode = "";
            String faultMessage = "";

            for (int i = 0; i < members.getLength(); ++i) {
                Element memberElement = (Element) members.item(i);
                Element memberNameElement = (Element) memberElement.getElementsByTagName("name").item(0);
                String memberName = memberNameElement.getTextContent();
                Element memberValue = (Element) memberElement.getElementsByTagName("value").item(0);

                if (memberName.equals("faultCode")) {
                    faultCode = memberValue.getTextContent();
                } else if (memberName.equals("faultString")) {
                    faultMessage = memberValue.getTextContent();
                }
            }

            throw new Exception("Server could not handle request. Fault code: " + faultCode + ". " + faultMessage);
        } else {
            Element responseParam = (Element) response.getElementsByTagName("param").item(0);
            Element value = (Element) responseParam.getElementsByTagName("value").item(0);
            NodeList type = value.getElementsByTagName("i4");

            if (type == null || type.getLength() != 1) {
                throw new Exception("Server responded with incorrect type.");
            }

            Element typeElement = (Element) type.item(0);
            return Integer.parseInt(typeElement.getTextContent());
        }
    }

    private static String getStringFromDoc(Document doc) throws Exception {
        StringWriter sw = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}