package com.opendxl.client.cli;

public class PromptArg {
    private String name;
    private String title;
    private boolean confirm;

    public PromptArg(String name, String title, boolean confirm) {
        this.name = name;
        this.title = title;
        this.confirm = confirm;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isConfirm() {
        return confirm;
    }

    public void setConfirm(boolean confirm) {
        this.confirm = confirm;
    }
}
