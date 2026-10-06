package com.classhack.exception;

import lombok.Getter;

@Getter
public class DuplicateHackathonException extends RuntimeException {
    private final String hackathonName;
    private final String existingCreator;

    public DuplicateHackathonException(String hackathonName, String existingCreator) {
        super("This hackathon is already created by '" + existingCreator + "' do you want to create it?");
        this.hackathonName = hackathonName;
        this.existingCreator = existingCreator;
    }
}
