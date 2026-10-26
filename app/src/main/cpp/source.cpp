//#include <jni.h>
//#include <cstdio>
//#include <cstdlib>
//#include "pkcs11.h"
//#include <dlfcn.h>
//#include <iostream>
//#include <cstring>
//// Global Variables
//static void *pkcs11Lib = NULL;
//static CK_FUNCTION_LIST_PTR pFunctions = NULL;
//static CK_SLOT_ID slotId;
//static CK_SESSION_HANDLE hSession = CK_INVALID_HANDLE;
//
//
////function to get the objects handle
//CK_OBJECT_HANDLE getObjectHandle(CK_OBJECT_CLASS objClass, CK_ATTRIBUTE_TYPE attrType, CK_BYTE_PTR attrValue, CK_ULONG attrValueLen) {
//    CK_OBJECT_HANDLE hObject = CK_INVALID_HANDLE;
//    CK_RV rv;
//    CK_OBJECT_HANDLE hObjects[10];
//    CK_ULONG ulObjectCount;
//    CK_ATTRIBUTE attrTemplate[] = {
//            {CKA_CLASS, &objClass, sizeof(objClass)},
//            {attrType, attrValue, attrValueLen}
//    };
//    CK_ULONG tempPrivateSize = sizeof(attrTemplate) / sizeof(CK_ATTRIBUTE);
//    rv = pFunctions->C_FindObjectsInit(hSession, attrTemplate, tempPrivateSize);
//    if (rv != CKR_OK) {
//        return CK_INVALID_HANDLE;
//    }
//
//    rv = pFunctions->C_FindObjects(hSession, &hObject, 10, &ulObjectCount);
//    if (rv != CKR_OK || ulObjectCount == 0) {
//        pFunctions->C_FindObjectsFinal(hSession);
//        return CK_INVALID_HANDLE;
//    }
//
//    CK_UTF8CHAR label[32];
//    CK_ATTRIBUTE readTempPrivate[] = {{CKA_LABEL, label, sizeof(label)}};
//    rv = pFunctions->C_GetAttributeValue(hSession, hObject, readTempPrivate, 1);
//    if (rv == CKR_OK) {
//        return hObject;
//    } else {
//        pFunctions->C_FindObjectsFinal(hSession);
//        return CK_INVALID_HANDLE;
//    }
//
//    pFunctions->C_FindObjectsFinal(hSession);
//    return hObject;
//}
//
//// Get Available Slot (Must Be Called Before Opening a Session)
//CK_SLOT_ID getSlot() {
//    CK_ULONG slotCount;
//    if (pFunctions->C_GetSlotList(CK_TRUE, nullptr, &slotCount) != CKR_OK || slotCount == 0) return JNI_FALSE;
//
//    auto *slots = (CK_SLOT_ID *) malloc(slotCount * sizeof(CK_SLOT_ID));
//    if (!slots) return JNI_FALSE;
//
//    if (pFunctions->C_GetSlotList(CK_TRUE, slots, &slotCount) != CKR_OK) {
//        free(slots);
//        return JNI_FALSE;
//    }
//
//    slotId = slots[0];  // Select the first slot
//    free(slots);
//    return slotId;
//}
//
//extern "C"
//{
//// Load PKCS#11 Library (Called Once at App Start)
//JNIEXPORT jboolean JNICALL Java_com_example_trustoken_1starter_TrusToken_loadLibrary(JNIEnv *env, jobject obj, jstring libPath) {
//    if (pkcs11Lib != nullptr) return JNI_TRUE;  // Already loaded
//
//    const char *nativeLibPath = (*env).GetStringUTFChars(libPath, NULL);
//    pkcs11Lib = dlopen(nativeLibPath, RTLD_LAZY);
//    (*env).ReleaseStringUTFChars(libPath, nativeLibPath);
//
//    if (!pkcs11Lib) return JNI_FALSE;
//
//    auto pGetFunctionList = (CK_C_GetFunctionList) dlsym(pkcs11Lib, "C_GetFunctionList");
//    if (!pGetFunctionList || pGetFunctionList(&pFunctions) != CKR_OK) return JNI_FALSE;
//
//    if (pFunctions->C_Initialize(nullptr) != CKR_OK) return JNI_FALSE;
//
//    return JNI_TRUE;
//}
//
//// Unload PKCS#11 Library (Called Once at App Exit)
//JNIEXPORT void JNICALL Java_com_example_trustoken_1starter_TrusToken_unloadLibrary(JNIEnv *env, jobject obj) {
//    if (pkcs11Lib) {
//        pFunctions->C_Finalize(nullptr);
//        dlclose(pkcs11Lib);
//        pkcs11Lib = nullptr;
//    }
//}
//
//// Open Session
//JNIEXPORT jboolean JNICALL Java_com_example_trustoken_1starter_TrusToken_openSession(JNIEnv *env, jobject obj) {
//    getSlot();
//    if (pFunctions->C_OpenSession(slotId, CKF_SERIAL_SESSION | CKF_RW_SESSION, nullptr, nullptr, &hSession) != CKR_OK)
//        return JNI_FALSE;
//    return JNI_TRUE;
//}
//
//// Close Session
//JNIEXPORT void JNICALL Java_com_example_trustoken_1starter_TrusToken_closeSession(JNIEnv *env, jobject obj) {
//    if (hSession != CK_INVALID_HANDLE) {
//        pFunctions->C_CloseSession(hSession);
//        hSession = CK_INVALID_HANDLE;
//    }
//}
//
//// User Login
//JNIEXPORT jboolean JNICALL Java_com_example_trustoken_1starter_TrusToken_login(JNIEnv *env, jobject obj, jstring pin) {
//    if (hSession == CK_INVALID_HANDLE) return JNI_FALSE;
//
//    const char *nativePin = (*env).GetStringUTFChars(pin, nullptr);
//    CK_RV rv = pFunctions->C_Login(hSession, CKU_USER, (CK_UTF8CHAR_PTR) nativePin, strlen(nativePin));
//    (*env).ReleaseStringUTFChars(pin, nativePin);
//
//    return (rv == CKR_OK) ? JNI_TRUE : JNI_FALSE;
//}
//
//// User Logout
//JNIEXPORT jboolean JNICALL Java_com_example_trustoken_1starter_TrusToken_logout(JNIEnv *env, jobject obj) {
//    if (hSession != CK_INVALID_HANDLE) {
//        CK_RV rv = pFunctions->C_Logout(hSession);
//        return (rv == CKR_OK) ? JNI_TRUE : JNI_FALSE;
//    }
//    return JNI_FALSE;
//}
//
//// Signing Data
//JNIEXPORT jstring JNICALL Java_com_example_trustoken_1starter_TrusToken_signData(JNIEnv *env, jobject obj, jstring data) {
//    if (hSession == CK_INVALID_HANDLE) return nullptr;
//
//    const char *nativeData = (*env).GetStringUTFChars(data, nullptr);
//
//    CK_OBJECT_HANDLE hPrivate = getObjectHandle(CKO_PRIVATE_KEY, CKA_SIGN, (CK_BYTE_PTR) nativeData, strlen(nativeData));
//
//    CK_MECHANISM mech = {CKM_RSA_PKCS, nullptr, 0};
//    if (pFunctions->C_SignInit(hSession, &mech, hPrivate) != CKR_OK) return nullptr;
//
//
//    CK_ULONG sigLen;
//    pFunctions->C_Sign(hSession, (CK_BYTE *)nativeData, strlen(nativeData), nullptr, &sigLen);
//
//    auto *signature = (CK_BYTE *) malloc(sigLen);
//    if (!signature) return nullptr;
//
//    if (pFunctions->C_Sign(hSession, (CK_BYTE_PTR) nativeData, strlen(nativeData), signature, &sigLen) != CKR_OK) {
//        free(signature);
//        return nullptr;
//    }
//
//    std::string hexSignature;
//    char hexBuffer[3];
//    for (CK_ULONG i = 0; i < sigLen; ++i) {
//        snprintf(hexBuffer, sizeof(hexBuffer), "%02X", signature[i]);
//        hexSignature.append(hexBuffer);
//    }
//
//    return env->NewStringUTF(hexSignature.c_str());
//}
//
//// Encryption Function
//JNIEXPORT jbyteArray JNICALL Java_com_example_trustoken_1starter_TrusToken_encryptData(JNIEnv *env, jobject obj, jbyteArray data) {
//    // Implement encryption logic using PKCS#11 encryption functions
//    return nullptr;
//}
//
//// Decryption Function
//JNIEXPORT jbyteArray JNICALL Java_com_example_trustoken_1starter_TrusToken_decryptData(JNIEnv *env, jobject obj, jbyteArray encryptedData) {
//    // Implement decryption logic using PKCS#11 decryption functions
//    return nullptr;
//}
//
//// Signature Verification
//JNIEXPORT jboolean JNICALL Java_com_example_trustoken_1starter_TrusToken_verifySignature(JNIEnv *env, jobject obj, jbyteArray data, jbyteArray signature) {
//    // Implement signature verification using PKCS#11 functions
//    return JNI_FALSE;
//}
//}
