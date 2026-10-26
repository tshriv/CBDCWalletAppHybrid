#include <jni.h>
#include <string>
#include <vector>
#include <dlfcn.h>
#include "cryptoki.h"
#include <sstream>
#include <iomanip>
#include <cstdlib>
#include <cstring>
#include <android/log.h>
#include <iostream>
#include <stdexcept>
#define LOG_TAG "MyLib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations for pointer cleanup
void cleanUp();

// Function pointer typedefs remain unchanged.
typedef int(*Connect_usb)(int);

typedef CK_RV (*Initialize)(CK_VOID_PTR);

typedef CK_RV (*GetSlotList)(CK_BBOOL, CK_SLOT_ID_PTR, CK_ULONG_PTR);

typedef CK_RV (*OpenSession)(CK_SLOT_ID, CK_FLAGS, CK_VOID_PTR, CK_NOTIFY, CK_SESSION_HANDLE_PTR);

typedef CK_RV (*Login)(CK_SESSION_HANDLE, CK_USER_TYPE, CK_UTF8CHAR_PTR, CK_ULONG);

typedef CK_RV (*FindObjectsInit)(CK_SESSION_HANDLE, CK_ATTRIBUTE_PTR, CK_ULONG);

typedef CK_RV (*FindObjects)(CK_SESSION_HANDLE, CK_OBJECT_HANDLE_PTR, CK_ULONG, CK_ULONG_PTR);

typedef CK_RV (*GetAttributeValue)(CK_SESSION_HANDLE, CK_OBJECT_HANDLE, CK_ATTRIBUTE_PTR, CK_ULONG);

typedef CK_RV (*FindObjectsFinal)(CK_SESSION_HANDLE);

typedef CK_RV (*SignInit)(CK_SESSION_HANDLE, CK_MECHANISM_PTR, CK_OBJECT_HANDLE);

typedef CK_RV (*Sign)(CK_SESSION_HANDLE, CK_BYTE_PTR, CK_ULONG, CK_BYTE_PTR, CK_ULONG_PTR);

typedef CK_RV (*VerifyInit)(CK_SESSION_HANDLE, CK_MECHANISM_PTR, CK_OBJECT_HANDLE);

typedef CK_RV (*Verify)(CK_SESSION_HANDLE, CK_BYTE_PTR, CK_ULONG, CK_BYTE_PTR, CK_ULONG);

typedef CK_RV (*EncryptInit)(CK_SESSION_HANDLE, CK_MECHANISM_PTR, CK_OBJECT_HANDLE);

typedef CK_RV (*Encrypt)(CK_SESSION_HANDLE, CK_BYTE_PTR, CK_ULONG, CK_BYTE_PTR, CK_ULONG_PTR);

typedef CK_RV (*DecryptInit)(CK_SESSION_HANDLE, CK_MECHANISM_PTR, CK_OBJECT_HANDLE);

typedef CK_RV (*Decrypt)(CK_SESSION_HANDLE, CK_BYTE_PTR, CK_ULONG, CK_BYTE_PTR, CK_ULONG_PTR);

typedef CK_RV (*Logout)(CK_SESSION_HANDLE);

typedef CK_RV (*CloseSession)(CK_SESSION_HANDLE);

typedef CK_RV (*Finalize)(CK_VOID_PTR);

// Global variables (consider encapsulating these in a class in a real application)
bool isInitialized = false;
CK_SESSION_HANDLE hSession = 0;
CK_OBJECT_HANDLE hPrivate = 0; // Handle for a private key.
CK_OBJECT_HANDLE hObject = 0;
CK_ULONG ulObjectCount = 0;
void *dlhandle = nullptr;
CK_BYTE *signature = new CK_BYTE[256];
CK_ULONG sigLen = 256;
CK_BYTE *encrypted = nullptr;
CK_BYTE *decrypted = nullptr;
CK_ULONG encLen = 0, decLen = 0;

// Global variables for plain text (be careful with globals in multi-threaded contexts)
const char *plain_data = nullptr;
const char *plain_data_encrypt = nullptr;

std::string certToHex(CK_BYTE_PTR data, CK_ULONG len) {
    std::stringstream ss;
    ss << std::hex;
    for (CK_ULONG i = 0; i < len; ++i)
        ss << std::setw(2) << std::setfill('0') << static_cast<int>(data[i]);
    return ss.str();
}
std::vector<CK_BYTE> hexStringToBytes(const std::string& hexString) {
    std::vector<CK_BYTE> bytes;

    // Ensure the hex string has an even length
    if (hexString.length() % 2 != 0) {
        LOGE("Invalid hex string length %zu", hexString.length());
        return bytes;
    }

    for (size_t i = 0; i < hexString.length(); i += 2) {
        std::string byteString = hexString.substr(i, 2);
        CK_BYTE byte = static_cast<CK_BYTE>(std::stoi(byteString, nullptr, 16));
        bytes.push_back(byte);
    }

    return bytes;
}

void cleanUp() {
    if (dlhandle != nullptr) {
        dlclose(dlhandle);
        dlhandle = nullptr;
    }
    // Free any allocated memory if needed
    if (encrypted) {
        delete[] encrypted;
        encrypted = nullptr;
    }
    if (decrypted) {
        delete[] decrypted;
        decrypted = nullptr;
    }
    hSession = 0;
    // Reset other globals if needed
}

// Helper to log an error with an optional error code and clean up before returning.
jstring logErrorAndCleanup(JNIEnv *env, const char *msg, CK_RV rv = CKR_OK) {
    if (rv != CKR_OK) {
        LOGE("%s (rv = 0x%lX)", msg, static_cast<unsigned long>(rv));
    } else {
        LOGE("%s", msg);
    }
    cleanUp();
    return env->NewStringUTF(msg);
}

// Helper function to load the library only once.
void *getLibraryHandle() {
    if (dlhandle == nullptr) {
        dlhandle = dlopen("liblsusbdemo.so", RTLD_NOW);
        if (dlhandle == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, "MyLib", "dlopen failed: %s", dlerror());
        }
    }
    return dlhandle;
}

CK_RV initializePKCS11() {
    if (isInitialized) {
        return CKR_OK;
    }
    auto c_initialize = (Initialize) dlsym(dlhandle, "C_Initialize");
    if (!c_initialize) {
        return CKR_FUNCTION_REJECTED;
    }
    CK_RV rv = c_initialize(nullptr);
    if (rv != CKR_OK) {
        return rv;
    }
    isInitialized = true;
    return CKR_OK;
}

CK_RV openSession(const char *token_pin, JNIEnv *env, jstring jStr) {

    if (hSession != 0) {
        return CKR_OK;
    }


    auto getSlotList = (GetSlotList) dlsym(dlhandle, "C_GetSlotList");
    auto c_openSession = (OpenSession) dlsym(dlhandle, "C_OpenSession");

    if (!getSlotList || !c_openSession) {
        logErrorAndCleanup(env, "Failed to find required symbols", CKR_FUNCTION_REJECTED);
        env->ReleaseStringUTFChars(jStr, token_pin);
        std::cerr << "Failed to find required symbols" << std::endl;
        return CKR_FUNCTION_REJECTED;
    }
    LOGE("%s", "initializing");
    CK_RV rv = initializePKCS11();
    if (rv != CKR_OK) {
        logErrorAndCleanup(env, "Failed to initialize pkcs#11", rv);
        env->ReleaseStringUTFChars(jStr, token_pin);
        std::cerr << "Failed to initialize PKCS#11" << rv << std::endl;
        return rv;
    }
    LOGE("%s","initialized");

//    CK_ULONG no_of_slots = 0;
//    CK_SLOT_ID slotlist[no_of_slots];
//    try {
//        LOGE("getSlotList called");
//        getSlotList(TRUE, slotlist, &no_of_slots);
//        LOGE("getSlotList returned");
//    }
//    catch (const std::exception& e) {
//        LOGE("Exception caught: %s", e.what());
//        logErrorAndCleanup(env, "Failed to get slot list", CKR_ARGUMENTS_BAD);
//        env->ReleaseStringUTFChars(jStr, token_pin);
//        std::cerr << "Failed to get slot list" << CKR_ARGUMENTS_BAD << std::endl;
//        return CKR_ARGUMENTS_BAD;
//    }
//    LOGE("no of slots %lu", no_of_slots);
//    if (no_of_slots == 0) {
//        printf("No slots found with tokens inserted\n");
//        logErrorAndCleanup(env, "No slots found with tokens inserted", CKR_SLOT_ID_INVALID);
//        return CKR_SLOT_ID_INVALID;
//    }
//    LOGE("openSession called");
//    //logErrorAndCleanup(env, "tokens inserted", CKR_OK);
////    CK_SLOT_ID slotlist[no_of_slots];
//    rv = getSlotList(CK_TRUE, slotlist, &no_of_slots);
//    if (rv != CKR_OK) {
//        logErrorAndCleanup(env, "Failed to get slot list", rv);
//        env->ReleaseStringUTFChars(jStr, token_pin);
//        std::cerr << "Failed to get slot list" << rv << std::endl;
//        return rv;
//    }
//    LOGE("slotlist[0] %lu", slotlist[0]);
//    LOGE("slot count %lu", no_of_slots);
    CK_SESSION_HANDLE session;
    rv = c_openSession(0, CKF_SERIAL_SESSION | CKF_RW_SESSION, nullptr, nullptr,
                       &session);
    if (rv != CKR_OK) {
        logErrorAndCleanup(env, "Failed to open session", rv);
        env->ReleaseStringUTFChars(jStr, token_pin);
        std::cerr << "Failed to open session" << rv << std::endl;
        return rv;
    }
    hSession = session;
    std::cout << "opened session" << std::endl;
    return CKR_OK;
}


extern "C" {

JNIEXPORT jint JNICALL
Java_com_example_trustoken_1starter_TrusToken_libint(JNIEnv *env, jobject mainActivityInstance,
                                                     jint fileDescriptor) {
    if (getLibraryHandle() == nullptr) {
        return -1;
    }
    auto Connect_usb_test = (Connect_usb) dlsym(dlhandle, "Connect_usb");
    if (Connect_usb_test == nullptr) {
        LOGE("dlsym(Connect_usb) failed: %s", dlerror());
        cleanUp();
        return -1;
    }
    int ret = Connect_usb_test(fileDescriptor);
    return ret;
}

JNIEXPORT jstring JNICALL
Java_com_example_trustoken_1starter_TrusToken_login(JNIEnv *env, jobject mainActivityInstance,
                                                    jstring jStr) {


    // Get token_pin from jstring and ensure it is released later.
    const char *token_pin = env->GetStringUTFChars(jStr, nullptr);
    if (!token_pin) {
        return env->NewStringUTF("Failed to get token_pin");
    }

    if (getLibraryHandle() == nullptr) {
        return env->NewStringUTF("Failed to load library");
    }

    LOGE("%s", token_pin);
    CK_RV rv = openSession(token_pin, env, jStr);
    if (rv != CKR_OK) {
        return logErrorAndCleanup(env, "Failed to initialize", rv);
    }
    LOGE("%s","Login called");
    auto c_login = (Login) dlsym(dlhandle, "C_Login");
    if (!c_login) {
        return logErrorAndCleanup(env, "Failed to find C_Login symbol");
    }

    rv = c_login(hSession, CKU_USER, (CK_BYTE_PTR) token_pin, strlen(token_pin));
//    env->ReleaseStringUTFChars(jStr, token_pin);  // Always release the string
    if (rv != CKR_OK) {
        return logErrorAndCleanup(env, "Failed to login", rv);
    }

    return env->NewStringUTF("Login Success");
}

JNIEXPORT jstring JNICALL
Java_com_example_trustoken_1starter_TrusToken_readCertificate(JNIEnv *env,
                                                              jobject mainActivityInstance) {
    if (getLibraryHandle() == nullptr) {
        return env->NewStringUTF("Failed to load library");
    }

    auto c_findObjectsInit = (FindObjectsInit) dlsym(dlhandle, "C_FindObjectsInit");
    auto c_findObjects = (FindObjects) dlsym(dlhandle, "C_FindObjects");
    auto c_getAttributeValue = (GetAttributeValue) dlsym(dlhandle, "C_GetAttributeValue");
    auto c_findObjectsFinal = (FindObjectsFinal) dlsym(dlhandle, "C_FindObjectsFinal");

    if (!c_findObjectsInit || !c_findObjects || !c_findObjectsFinal || !c_getAttributeValue) {
        return logErrorAndCleanup(env, "Failed to find symbols");
    }

    CK_OBJECT_CLASS certClass = CKO_CERTIFICATE;
    CK_CERTIFICATE_TYPE certType = CKC_X_509;
    CK_ATTRIBUTE certTemplate[] = {
            {CKA_CLASS,            &certClass, sizeof(certClass)},
            {CKA_CERTIFICATE_TYPE, &certType,  sizeof(certType)}
    };

    CK_RV rv = c_findObjectsInit(hSession, certTemplate, 2);
    if (rv != CKR_OK) {
        return logErrorAndCleanup(env, "Failed to initialize object search", rv);
    }

    CK_OBJECT_HANDLE certObj;
    CK_ULONG objCount = 0;
    rv = c_findObjects(hSession, &certObj, 1, &objCount);
    if (rv != CKR_OK || objCount == 0) {
        c_findObjectsFinal(hSession);
        return logErrorAndCleanup(env, "Failed to find certificate object", rv);
    }

    rv = c_findObjectsFinal(hSession);
    if (rv != CKR_OK) {
        return logErrorAndCleanup(env, "Failed to finalize object search", rv);
    }

    CK_ATTRIBUTE certValueTemplate[] = {
            {CKA_VALUE, NULL_PTR, 0}
    };

    rv = c_getAttributeValue(hSession, certObj, certValueTemplate, 1);
    if (rv != CKR_OK) {
        return logErrorAndCleanup(env, "Failed to get certificate value size", rv);
    }

    // Allocate memory for the certificate value.
    auto certValue = (CK_BYTE_PTR) malloc(certValueTemplate[0].ulValueLen);
    if (certValue == nullptr) {
        return logErrorAndCleanup(env, "Failed to allocate memory for certificate value");
    }

    certValueTemplate[0].pValue = certValue;
    rv = c_getAttributeValue(hSession, certObj, certValueTemplate, 1);
    if (rv != CKR_OK) {
        free(certValue);
        return logErrorAndCleanup(env, "Failed to get certificate value", rv);
    }

    std::string hexCertValue = certToHex(certValue, certValueTemplate[0].ulValueLen);
    free(certValue);

    return env->NewStringUTF(hexCertValue.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_trustoken_1starter_TrusToken_logout(JNIEnv *env, jobject thiz) {
    if (getLibraryHandle() == nullptr) {
        return env->NewStringUTF("Failed to load library");
    }

    auto logout = (Logout) dlsym(dlhandle, "C_Logout");
    auto closeSession = (CloseSession) dlsym(dlhandle, "C_CloseSession");
    auto finalize = (Finalize) dlsym(dlhandle, "C_Finalize");

    if (!logout || !closeSession || !finalize) {
        return logErrorAndCleanup(env, "Failed to find symbols");
    }

    CK_RV rv = logout(hSession);
    if (rv != CKR_OK) {
        return logErrorAndCleanup(env, "Failed to logout", rv);
    }

    rv = closeSession(hSession);
    if (rv != CKR_OK) {
        return logErrorAndCleanup(env, "Failed to close session", rv);
    }
    hSession = 0;

//    rv = finalize(NULL_PTR);
//    if (rv != CKR_OK) {
//        return logErrorAndCleanup(env, "Failed to finalize", rv);
//    }

//    cleanUp();
    return env->NewStringUTF("Logged out Successfully");
}

JNIEXPORT jstring JNICALL
Java_com_example_trustoken_1starter_TrusToken_signData(JNIEnv *env, jobject mainActivityInstance) {

    if (getLibraryHandle() == nullptr) {
        return env->NewStringUTF("Failed to load library");
    }

    // Retrieve plain text from TrusToken.
    jclass mainActivityCls = env->GetObjectClass(mainActivityInstance);
    jmethodID jmethodId_PlainText = env->GetMethodID(mainActivityCls, "getPlainText",
                                                     "()Ljava/lang/String;");
    if (jmethodId_PlainText == nullptr) {
        return env->NewStringUTF("Failed to retrieve plain text method");
    }

    auto jPlainText = (jstring) env->CallObjectMethod(mainActivityInstance, jmethodId_PlainText);
    if (jPlainText == nullptr) {
        return env->NewStringUTF("Plain text not provided");
    }
    plain_data = env->GetStringUTFChars(jPlainText, nullptr);
    if (plain_data == nullptr) {
        return env->NewStringUTF("Failed to get plain text");
    }

    // Obtain required function pointers.
    auto c_findObjectsInit = (FindObjectsInit) dlsym(dlhandle, "C_FindObjectsInit");
    auto c_findObjects = (FindObjects) dlsym(dlhandle, "C_FindObjects");
    auto c_getAttributeValue = (GetAttributeValue) dlsym(dlhandle, "C_GetAttributeValue");
    auto findObjectsFinal = (FindObjectsFinal) dlsym(dlhandle, "C_FindObjectsFinal");
    auto signInit = (SignInit) dlsym(dlhandle, "C_SignInit");
    Sign sign = (Sign) dlsym(dlhandle, "C_Sign");

    if (!c_findObjectsInit || !c_findObjects || !findObjectsFinal || !c_getAttributeValue ||
        !signInit || !sign) {
        env->ReleaseStringUTFChars(jPlainText, plain_data);
        return logErrorAndCleanup(env, "Failed to find required symbols");
    }

    // Search for a private key object.
    CK_OBJECT_CLASS keyClassPriv = CKO_PRIVATE_KEY;
    CK_ATTRIBUTE templPriv[] = {{CKA_CLASS, &keyClassPriv, sizeof(keyClassPriv)}};
    CK_ULONG templPrivateSize = sizeof(templPriv) / sizeof(CK_ATTRIBUTE);

    CK_RV rv = c_findObjectsInit(hSession, templPriv, templPrivateSize);
    if (rv != CKR_OK) {
        env->ReleaseStringUTFChars(jPlainText, plain_data);
        return logErrorAndCleanup(env, "Failed to initiate find objects", rv);
    }

    rv = c_findObjects(hSession, &hObject, 1, &ulObjectCount);
    if (rv != CKR_OK || ulObjectCount == 0) {
        findObjectsFinal(hSession);
        env->ReleaseStringUTFChars(jPlainText, plain_data);
        return logErrorAndCleanup(env, "Failed to find private key object", rv);
    }

    // Read an attribute (e.g. label) to confirm the object.
    CK_UTF8CHAR label[32];
    CK_ATTRIBUTE readtemplPrivate[] = {{CKA_LABEL, label, sizeof(label)}};
    rv = c_getAttributeValue(hSession, hObject, readtemplPrivate, 1);
    if (rv == CKR_OK) {
        hPrivate = hObject;
    } else {
        findObjectsFinal(hSession);
        env->ReleaseStringUTFChars(jPlainText, plain_data);
        return logErrorAndCleanup(env, "Failed to read private key object", rv);
    }
    rv = findObjectsFinal(hSession);
    if (rv != CKR_OK) {
        env->ReleaseStringUTFChars(jPlainText, plain_data);
        return logErrorAndCleanup(env, "Failed to finalize find objects", rv);
    }

    // Initialize signing.
    CK_MECHANISM mech = {CKM_SHA256_RSA_PKCS};
    rv = signInit(hSession, &mech, hPrivate);
    if (rv != CKR_OK) {
        env->ReleaseStringUTFChars(jPlainText, plain_data);
        return logErrorAndCleanup(env, "Failed to initialize signing", rv);
    }

    rv = sign(hSession, (CK_BYTE *) plain_data, strlen(plain_data), signature, &sigLen);
    // Release the plain text regardless of sign result.
    env->ReleaseStringUTFChars(jPlainText, plain_data);
    if (rv != CKR_OK) {
        return logErrorAndCleanup(env, "Failed to sign the data", rv);
    }

    // Convert the signature to a hex string.
    std::string hexSignature;
    char hexBuffer[3];
    for (CK_ULONG i = 0; i < sigLen; ++i) {
        snprintf(hexBuffer, sizeof(hexBuffer), "%02X", signature[i]);
        hexSignature.append(hexBuffer);
    }
    LOGE("signature length %lu", sigLen);

    return env->NewStringUTF(hexSignature.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_trustoken_1starter_TrusToken_verify(JNIEnv *env, jobject thiz, jstring jsig, jstring data) {
    if (getLibraryHandle() == nullptr) {
        return env->NewStringUTF("Failed to load library");
    }
    const char *sign = env->GetStringUTFChars(jsig, nullptr);
    if (sign == nullptr) {
        return env->NewStringUTF("Failed to get signature");
    }
    const char *plain_text = env->GetStringUTFChars(data, nullptr);
    if (plain_text == nullptr) {
        return env->NewStringUTF("Failed to get plain text");
    }
    std::vector<CK_BYTE> originalSignature = hexStringToBytes(sign);
    if (originalSignature.empty()) {
        return env->NewStringUTF("Invalid signature format");
    }
    CK_BYTE* signaturePtr = originalSignature.data();
    CK_ULONG signatureLen = originalSignature.size();
    LOGE("signature length %lu", signatureLen);


    auto verifyInit = (VerifyInit) dlsym(dlhandle, "C_VerifyInit");
    auto verify = (Verify) dlsym(dlhandle, "C_Verify");

//    if (signature == NULL_PTR) {
//        return env->NewStringUTF("Signature not found");
//    }

    CK_MECHANISM mech = {CKM_SHA256_RSA_PKCS};
    CK_RV rv = verifyInit(hSession, &mech, 5000);
    if (rv != CKR_OK) {
        return logErrorAndCleanup(env, "Failed to initialize verify", rv);
    }

    rv = verify(hSession, (CK_BYTE_PTR) plain_text, strlen(plain_text), signaturePtr, sigLen);
    if (rv != CKR_OK) {
        return env->NewStringUTF("Verification failed");
    }

    return env->NewStringUTF("Verified");
}

JNIEXPORT jstring JNICALL
Java_com_example_trustoken_1starter_TrusToken_encrypt(JNIEnv *env, jobject mainActivityInstance
) {

    if (getLibraryHandle() == nullptr) {
        return env->NewStringUTF("Failed to load library");
    }
    // Retrieve plain text for encryption.
    jclass mainActivityCls = env->GetObjectClass(mainActivityInstance);
    jmethodID jmethodId_PlainText = env->GetMethodID(mainActivityCls, "getPlainText",
                                                     "()Ljava/lang/String;");
    if (jmethodId_PlainText == nullptr) {
        return env->NewStringUTF("Failed to retrieve plain text method");
    }

    auto jPlainText = (jstring) env->CallObjectMethod(mainActivityInstance, jmethodId_PlainText);
    if (jPlainText == nullptr) {
        return env->NewStringUTF("Plain text not provided");
    }
    plain_data_encrypt = env->GetStringUTFChars(jPlainText, nullptr);
    if (plain_data_encrypt == nullptr) {
        return env->NewStringUTF("Failed to get plain text for encryption");
    }

    // Get encryption functions.
    auto encryptInit = (EncryptInit) dlsym(dlhandle, "C_EncryptInit");
    auto encrypt = (Encrypt) dlsym(dlhandle, "C_Encrypt");
    if (!encryptInit || !encrypt) {
        env->ReleaseStringUTFChars(jPlainText, plain_data_encrypt);
        return logErrorAndCleanup(env, "Failed to find encryption symbols");
    }

    CK_MECHANISM mech = {CKM_SHA256_RSA_PKCS};
    CK_RV rv = encryptInit(hSession, &mech, 5000);
    if (rv != CKR_OK) {
        env->ReleaseStringUTFChars(jPlainText, plain_data_encrypt);
        return logErrorAndCleanup(env, "Failed to initialize encryption", rv);
    }

    // First call to determine required buffer size.
    rv = encrypt(hSession, (CK_BYTE_PTR) plain_data_encrypt, strlen(plain_data_encrypt), NULL,
                 &encLen);
    if (rv != CKR_OK) {
        env->ReleaseStringUTFChars(jPlainText, plain_data_encrypt);
        return logErrorAndCleanup(env, "Failed to get encryption buffer size", rv);
    }

    encrypted = new CK_BYTE[encLen];
    rv = encrypt(hSession, (CK_BYTE_PTR) plain_data_encrypt, strlen(plain_data_encrypt), encrypted,
                 &encLen);
    env->ReleaseStringUTFChars(jPlainText, plain_data_encrypt);
    if (rv != CKR_OK) {
        delete[] encrypted;
        encrypted = nullptr;
        return logErrorAndCleanup(env, "Failed to encrypt data", rv);
    }

    // Convert encrypted data to hex.
    std::string hexEncryptedData;
    char hexBuffer[3];
    for (CK_ULONG i = 0; i < encLen; ++i) {
        snprintf(hexBuffer, sizeof(hexBuffer), "%02X", encrypted[i]);
        hexEncryptedData.append(hexBuffer);
    }

    return env->NewStringUTF(hexEncryptedData.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_trustoken_1starter_TrusToken_decrypt(JNIEnv *env, jobject thiz, jstring jStr) {
    if (getLibraryHandle() == nullptr) {
        return env->NewStringUTF("Failed to load library");
    }
    const char *encrypted_data = env->GetStringUTFChars(jStr, nullptr);
    if (encrypted_data == nullptr) {
        return env->NewStringUTF("Failed to get encrypted data");
    }

    // Convert hex string to byte array
    size_t encrypted_data_len = strlen(encrypted_data) / 2;
    encrypted = new CK_BYTE[encrypted_data_len];
    for (size_t i = 0; i < encrypted_data_len; ++i) {
        sscanf(&encrypted_data[2 * i], "%2hhx", &encrypted[i]);
    }
    encLen = encrypted_data_len;

    auto decryptInit = (DecryptInit) dlsym(dlhandle, "C_DecryptInit");
    auto decrypt = (Decrypt) dlsym(dlhandle, "C_Decrypt");
    if (!decryptInit || !decrypt) {
        env->ReleaseStringUTFChars(jStr, encrypted_data);
        return logErrorAndCleanup(env, "Failed to find decryption symbols");
    }

    CK_MECHANISM mech = {CKM_SHA256_RSA_PKCS};
    CK_RV rv = decryptInit(hSession, &mech, hPrivate);
    if (rv != CKR_OK) {
        env->ReleaseStringUTFChars(jStr, encrypted_data);
        return logErrorAndCleanup(env, "Failed to initialize decryption", rv);
    }

    // First call to get the size required.
    rv = decrypt(hSession, encrypted, encLen, nullptr, &decLen);
    if (rv != CKR_OK) {
        env->ReleaseStringUTFChars(jStr, encrypted_data);
        return logErrorAndCleanup(env, "Failed to get decryption buffer size", rv);
    }

    decrypted = new CK_BYTE[decLen];
    rv = decrypt(hSession, encrypted, encLen, decrypted, &decLen);
    env->ReleaseStringUTFChars(jStr, encrypted_data);
    if (rv != CKR_OK) {
        delete[] decrypted;
        decrypted = nullptr;
        return logErrorAndCleanup(env, "Failed to decrypt data", rv);
    }

    // Convert decrypted data to hex.
    std::string hexDecryptedData;
    char hexBuffer[3];
    for (CK_ULONG i = 0; i < decLen; ++i) {
        snprintf(hexBuffer, sizeof(hexBuffer), "%02X", decrypted[i]);
        hexDecryptedData.append(hexBuffer);
    }

    return env->NewStringUTF(hexDecryptedData.c_str());
}

// ----------------------------------------------------------------------------
// Exports for package com.ts.cbdcwalletapp.security.TrusToken
// ----------------------------------------------------------------------------

JNIEXPORT jint JNICALL
Java_com_ts_cbdcwalletapp_security_TrusToken_libint(JNIEnv *env, jobject thiz, jint fileDescriptor) {
    return Java_com_example_trustoken_1starter_TrusToken_libint(env, thiz, fileDescriptor);
}

JNIEXPORT jstring JNICALL
Java_com_ts_cbdcwalletapp_security_TrusToken_login(JNIEnv *env, jobject thiz, jstring jStr) {
    return Java_com_example_trustoken_1starter_TrusToken_login(env, thiz, jStr);
}

JNIEXPORT jstring JNICALL
Java_com_ts_cbdcwalletapp_security_TrusToken_readCertificate(JNIEnv *env, jobject thiz) {
    return Java_com_example_trustoken_1starter_TrusToken_readCertificate(env, thiz);
}

JNIEXPORT jstring JNICALL
Java_com_ts_cbdcwalletapp_security_TrusToken_logout(JNIEnv *env, jobject thiz) {
    return Java_com_example_trustoken_1starter_TrusToken_logout(env, thiz);
}

JNIEXPORT jstring JNICALL
Java_com_ts_cbdcwalletapp_security_TrusToken_signData(JNIEnv *env, jobject thiz) {
    return Java_com_example_trustoken_1starter_TrusToken_signData(env, thiz);
}

JNIEXPORT jstring JNICALL
Java_com_ts_cbdcwalletapp_security_TrusToken_verify(JNIEnv *env, jobject thiz, jstring jsig, jstring data) {
    return Java_com_example_trustoken_1starter_TrusToken_verify(env, thiz, jsig, data);
}

JNIEXPORT jstring JNICALL
Java_com_ts_cbdcwalletapp_security_TrusToken_encrypt(JNIEnv *env, jobject thiz) {
    return Java_com_example_trustoken_1starter_TrusToken_encrypt(env, thiz);
}

JNIEXPORT jstring JNICALL
Java_com_ts_cbdcwalletapp_security_TrusToken_decrypt(JNIEnv *env, jobject thiz, jstring jStr) {
    return Java_com_example_trustoken_1starter_TrusToken_decrypt(env, thiz, jStr);
}

} // extern "C"
