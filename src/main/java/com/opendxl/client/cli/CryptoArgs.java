package com.opendxl.client.cli;

import com.opendxl.client.cli.certs.CsrAndPrivateKeyGenerator;
import org.bouncycastle.operator.OperatorCreationException;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class CryptoArgs {

    @CommandLine.Option(names = {"-f", "--file-prefix"}, paramLabel = "PREFIX",
            description = "file prefix to use for CSR, key, and cert files. (default: client)", defaultValue = "client")
    private String filePrefix;

    @CommandLine.Option(names = {"-s", "--san"}, paramLabel = "NAME",
            description = "add Subject Alternative Name(s) to the CSR", arity = "0..*")
    private List<String> subjectAlternativeNames;

    @CommandLine.Option(names = {"-P", "--passphrase"}, paramLabel = "PASS", description = "private key passphrase",
            interactive = true)
    private String passphrase;

    @CommandLine.Option(names = "--country", paramLabel = "COUNTRY",
            description = "Country (C) to use in the CSR's Subject DN")
    private String country;

    @CommandLine.Option(names = "--state-or-province", paramLabel = "STATE",
            description = "State or province (ST) to use in the CSR's Subject DN")
    private String stateOrProvince;

    @CommandLine.Option(names = "--locality", paramLabel = "LOCALITY",
            description = "Locality (L) to use in the CSR's Subject DN")
    private String locality;

    @CommandLine.Option(names = "--organization", paramLabel = "ORG",
            description = "Organization (O) to use in the CSR's Subject DN")
    private String organization;

    @CommandLine.Option(names = "--organizational-unit", paramLabel = "ORG_UNIT",
            description = "Organizational Unit (OU) to use in the CSR's Subject DN")
    private String organizationalUnit;

    @CommandLine.Option(names = "--email-address", paramLabel = "EMAIL",
            description = "e-mail address to use in the CSR's Subject DN")
    private String email;

    public String generateCsrAndPrivateKey(String configDir, String commonName)
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, OperatorCreationException,
            IOException {
        CsrAndPrivateKeyGenerator csrAndPrivateKeyGenerator = new CsrAndPrivateKeyGenerator(
                generateX509DistinguishedNames(commonName), this.subjectAlternativeNames);
        csrAndPrivateKeyGenerator.saveCsrAndPrivateKey(csrFileName(configDir), privateKeyFileName(configDir),
                this.passphrase);

        return csrAndPrivateKeyGenerator.getCsrAsPemString();
    }

    private String generateX509DistinguishedNames(String commonName) {
        // CN=localhost, L=Beaverton, O=McAfee Inc., OU=Client, C=US
        StringBuilder sb = new StringBuilder();
        sb.append("CN=").append(commonName);
        // TODO add other values
        return sb.toString();
    }

    public String privateKeyFileName(String configDir) {
        return configDir + File.separatorChar + this.filePrefix + ".key";
    }

    public String csrFileName(String configDir) {
        return configDir + File.separatorChar + this.filePrefix + ".csr";
    }

    public String certFileName(String configDir) {
        return configDir + File.separatorChar + this.filePrefix + ".crt";
    }

    public List<String> getSubjectAlternativeNames() {
        return subjectAlternativeNames;
    }

    public void setSubjectAlternativeNames(List<String> subjectAlternativeNames) {
        this.subjectAlternativeNames = subjectAlternativeNames;
    }

    public String getFilePrefix() {
        return filePrefix;
    }

    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getStateOrProvince() {
        return stateOrProvince;
    }

    public void setStateOrProvince(String stateOrProvince) {
        this.stateOrProvince = stateOrProvince;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getOrganizationalUnit() {
        return organizationalUnit;
    }

    public void setOrganizationalUnit(String organizationalUnit) {
        this.organizationalUnit = organizationalUnit;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
