#include "node_config.h"
#include "child_profile.h"

Preferences NodeConfig::s_prefs;
bool NodeConfig::s_isProvisioned = false;
String NodeConfig::s_childId = "";
String NodeConfig::s_parentId = "";
String NodeConfig::s_parentNodeIp = "";
int32_t NodeConfig::s_parentNodePort = 8080;
String NodeConfig::s_parentNodeEndpointId = "";
String NodeConfig::s_authToken = "";
int32_t NodeConfig::s_configVersion = 0;
uint32_t NodeConfig::s_tamperAuthorizedUntil = 0;
String NodeConfig::s_childName = "Demo Child";
int32_t NodeConfig::s_childAge = 8;
String NodeConfig::s_bloodGroup = "O+";
String NodeConfig::s_emergencyPhone = "+91 98765 43210";

void NodeConfig::init() {
    Serial.println("[NVS] Loading SafeBand node persistent configuration...");
    if (s_prefs.begin("safeband", false)) {
        s_isProvisioned = s_prefs.getBool("provisioned", false);
        s_childId = s_prefs.getString("child_id", "CHILD-UNPROVISIONED");
        s_parentId = s_prefs.getString("parent_id", "");
        s_parentNodeIp = s_prefs.getString("pn_ip", "");
        s_parentNodePort = s_prefs.getInt("pn_port", 8080);
        s_parentNodeEndpointId = s_prefs.getString("pn_ep_id", "");
        s_authToken = s_prefs.getString("auth_token", "");
        s_configVersion = s_prefs.getInt("config_ver", 0);
        s_childName = s_prefs.getString("ch_name", "Demo Child");
        s_childAge = s_prefs.getInt("ch_age", 8);
        s_bloodGroup = s_prefs.getString("ch_blood", "O+");
        s_emergencyPhone = s_prefs.getString("ch_phone", "+91 98765 43210");
        // Fail-secure: On reboot, reset maintenance authorization window.
        // Device must return to active PROTECTED state.
        s_tamperAuthorizedUntil = 0;
        s_prefs.putUInt("t_auth_until", 0);
        s_prefs.end();

        // Sync local in-memory profile
        ChildProfile prof;
        strncpy(prof.childId, s_childId.c_str(), sizeof(prof.childId) - 1);
        prof.childId[sizeof(prof.childId) - 1] = '\0';
        strncpy(prof.displayName, s_childName.c_str(), sizeof(prof.displayName) - 1);
        prof.displayName[sizeof(prof.displayName) - 1] = '\0';
        prof.age = s_childAge;
        strncpy(prof.bloodGroup, s_bloodGroup.c_str(), sizeof(prof.bloodGroup) - 1);
        prof.bloodGroup[sizeof(prof.bloodGroup) - 1] = '\0';
        strncpy(prof.emergencyContactPhone, s_emergencyPhone.c_str(), sizeof(prof.emergencyContactPhone) - 1);
        prof.emergencyContactPhone[sizeof(prof.emergencyContactPhone) - 1] = '\0';
        strncpy(prof.deviceId, s_childId.c_str(), sizeof(prof.deviceId) - 1);
        prof.deviceId[sizeof(prof.deviceId) - 1] = '\0';
        SafeBandNodeIdentity::setLocalProfile(prof);

        Serial.printf("[NVS] Config loaded: Provisioned=%s, ChildID=%s, Name=%s, Age=%d, Blood=%s, Ver=%d\n",
            s_isProvisioned ? "YES" : "NO",
            s_childId.c_str(),
            s_childName.c_str(),
            s_childAge,
            s_bloodGroup.c_str(),
            s_configVersion
        );
    } else {
        Serial.println("[NVS] WARN: Could not open NVS namespace 'safeband'");
    }
}

bool NodeConfig::isProvisioned() {
    return s_isProvisioned;
}

String NodeConfig::getChildId() {
    return s_childId;
}

String NodeConfig::getParentId() {
    return s_parentId;
}

String NodeConfig::getParentNodeIp() {
    return s_parentNodeIp;
}

int32_t NodeConfig::getParentNodePort() {
    return s_parentNodePort;
}

String NodeConfig::getParentNodeEndpointId() {
    return s_parentNodeEndpointId;
}

String NodeConfig::getAuthToken() {
    return s_authToken;
}

int32_t NodeConfig::getConfigVersion() {
    return s_configVersion;
}

uint32_t NodeConfig::getTamperAuthorizedUntil() {
    return s_tamperAuthorizedUntil;
}

String NodeConfig::getChildName() {
    return s_childName;
}

int32_t NodeConfig::getChildAge() {
    return s_childAge;
}

String NodeConfig::getBloodGroup() {
    return s_bloodGroup;
}

String NodeConfig::getEmergencyPhone() {
    return s_emergencyPhone;
}

bool NodeConfig::saveChildDetails(
    const String& childName,
    int32_t age,
    const String& bloodGroup,
    const String& emergencyPhone
) {
    if (!s_prefs.begin("safeband", false)) {
        Serial.println("[NVS] ERR: Failed to open NVS to save child details");
        return false;
    }

    if (childName.length() > 0) {
        s_childName = childName;
        s_prefs.putString("ch_name", s_childName);
    }
    if (age > 0 && age < 120) {
        s_childAge = age;
        s_prefs.putInt("ch_age", s_childAge);
    }
    if (bloodGroup.length() > 0) {
        s_bloodGroup = bloodGroup;
        s_prefs.putString("ch_blood", s_bloodGroup);
    }
    if (emergencyPhone.length() > 0) {
        s_emergencyPhone = emergencyPhone;
        s_prefs.putString("ch_phone", s_emergencyPhone);
    }
    s_prefs.end();

    // Update in-memory profile
    ChildProfile prof = SafeBandNodeIdentity::getLocalProfile();
    strncpy(prof.displayName, s_childName.c_str(), sizeof(prof.displayName) - 1);
    prof.displayName[sizeof(prof.displayName) - 1] = '\0';
    prof.age = s_childAge;
    strncpy(prof.bloodGroup, s_bloodGroup.c_str(), sizeof(prof.bloodGroup) - 1);
    prof.bloodGroup[sizeof(prof.bloodGroup) - 1] = '\0';
    strncpy(prof.emergencyContactPhone, s_emergencyPhone.c_str(), sizeof(prof.emergencyContactPhone) - 1);
    prof.emergencyContactPhone[sizeof(prof.emergencyContactPhone) - 1] = '\0';
    SafeBandNodeIdentity::setLocalProfile(prof);

    Serial.printf("[NVS] Stored child details in ESP32: Name=%s, Age=%d, Blood=%s, Phone=%s\n",
        s_childName.c_str(), s_childAge, s_bloodGroup.c_str(), s_emergencyPhone.c_str());
    return true;
}

bool NodeConfig::saveProvisioning(
    const String& childId,
    const String& parentId,
    const String& parentNodeIp,
    int32_t parentNodePort,
    const String& parentNodeEndpointId,
    const String& authToken,
    int32_t configVersion
) {
    if (childId.length() == 0) return false;

    if (!s_prefs.begin("safeband", false)) {
        Serial.println("[NVS] ERR: Failed to open NVS for writing");
        return false;
    }

    s_prefs.putBool("provisioned", true);
    s_prefs.putString("child_id", childId);
    s_prefs.putString("parent_id", parentId);
    s_prefs.putString("pn_ip", parentNodeIp);
    s_prefs.putInt("pn_port", parentNodePort);
    s_prefs.putString("pn_ep_id", parentNodeEndpointId);
    s_prefs.putString("auth_token", authToken);
    s_prefs.putInt("config_ver", configVersion);
    s_prefs.end();

    s_isProvisioned = true;
    s_childId = childId;
    s_parentId = parentId;
    s_parentNodeIp = parentNodeIp;
    s_parentNodePort = parentNodePort;
    s_parentNodeEndpointId = parentNodeEndpointId;
    s_authToken = authToken;
    s_configVersion = configVersion;

    Serial.printf("[NVS] Successfully provisioned! ChildID: %s, Endpoint: %s:%d (Ver: %d)\n",
        childId.c_str(), parentNodeIp.c_str(), parentNodePort, configVersion);
    return true;
}

void NodeConfig::setTamperAuthorizedUntil(uint32_t expiryEpochOrMs) {
    s_tamperAuthorizedUntil = expiryEpochOrMs;
    if (s_prefs.begin("safeband", false)) {
        s_prefs.putUInt("t_auth_until", expiryEpochOrMs);
        s_prefs.end();
    }
}

void NodeConfig::clearConfiguration() {
    if (s_prefs.begin("safeband", false)) {
        s_prefs.clear();
        s_prefs.end();
    }
    s_isProvisioned = false;
    s_childId = "CHILD-UNPROVISIONED";
    s_parentId = "";
    s_parentNodeIp = "";
    s_parentNodePort = 8080;
    s_parentNodeEndpointId = "";
    s_authToken = "";
    s_configVersion = 0;
    s_tamperAuthorizedUntil = 0;
    s_childName = "Demo Child";
    s_childAge = 8;
    s_bloodGroup = "O+";
    s_emergencyPhone = "+91 98765 43210";
    Serial.println("[NVS] Configuration cleared and reset to defaults.");
}

bool NodeConfig::verifyAuthToken(const String& token) {
    if (s_authToken.length() == 0) {
        // If not provisioned with a token yet, allow initial configuration
        return true;
    }
    return (s_authToken == token);
}
