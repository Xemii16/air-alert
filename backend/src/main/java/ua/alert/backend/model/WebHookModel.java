package ua.alert.backend.model;

public class WebHookModel {

    private String webHookUrl;

    public WebHookModel() {
    }

    public WebHookModel(String webHookUrl) {
        this.webHookUrl = webHookUrl;
    }

    public String getWebHookUrl() {
        return webHookUrl;
    }

    public void setWebHookUrl(String webHookUrl) {
        this.webHookUrl = webHookUrl;
    }
}
