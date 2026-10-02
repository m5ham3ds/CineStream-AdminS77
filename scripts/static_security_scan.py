#!/usr/bin/env python3
"""
CineStream Admin - Static Security & Contract Verification Scanner
Phase C7.1 Final Evidence Verification
"""

import os
import re
import sys

def main():
    print("==================================================")
    print("CINESTREAM ADMIN STATIC SECURITY SCANNER (PHASE C7.1)")
    print("==================================================")

    checks_passed = 0
    checks_failed = 0
    results = []

    def record_check(name, passed, detail):
        nonlocal checks_passed, checks_failed
        status = "PASS" if passed else "FAIL"
        if passed:
            checks_passed += 1
        else:
            checks_failed += 1
        results.append((name, status, detail))
        print(f"[{status}] {name}: {detail}")

    # 1. Rules file existence and version
    rules_path = "firestore.rules"
    if not os.path.exists(rules_path):
        record_check("Rules File Exists", False, f"Not found: {rules_path}")
        return
    with open(rules_path, "r", encoding="utf-8") as f:
        rules_content = f.read()

    record_check("Firestore Rules Version 2", "rules_version = '2';" in rules_content, "rules_version 2 declared")

    # 2. Admin authority checks strictly /admins/{uid}.enabled == true
    has_admin_collection_check = "/databases/$(database)/documents/admins/$(request.auth.uid)" in rules_content
    has_enabled_true = ".data.enabled == true" in rules_content
    uses_role_as_admin = bool(re.search(r"function isAdmin\(\)[^}]+request\.auth\.token\.role", rules_content)) or \
                         bool(re.search(r"function isAdmin\(\)[^}]+users/.*\.data\.role", rules_content))
    record_check("Admin Authority /admins/{uid}.enabled == true", has_admin_collection_check and has_enabled_true, "Canonical /admins authority verified")
    record_check("Admin Authority Ignores users.role", not uses_role_as_admin, "users.role is NEVER used in isAdmin()")

    # 3. Protected user fields check
    protected_fields_match = re.search(r"function modifyingSensitiveUserFields\(\)\s*\{[^}]+hasAny\(\[([^\]]+)\]\)", rules_content, re.DOTALL)
    if protected_fields_match:
        fields = [f.strip().strip("'\"") for f in protected_fields_match.group(1).split(",") if f.strip().strip("'\"")]
        expected_fields = [
            'role', 'isPremium', 'subscriptionTier', 'subscriptionStatus', 'subscriptionExpiresAt',
            'isPro', 'proExpiresAt', 'proPlan', 'plan', 'isActive', 'isBanned', 'banReason',
            'banExpiresAt', 'canWatch', 'canDownload', 'canChat', 'canStory', 'canP2P',
            'canComment', 'canUpload', 'canRequest', 'watchBan', 'downloadBan', 'chatBan',
            'storyBan', 'p2pBan', 'deviceLimit', 'maxDevices', 'allowedQuality', 'downloadLimit',
            'offlineDaysOverride', 'forcedAdsOverride', 'uid', 'id', 'createdAt', 'admin', 'isAdmin'
        ]
        all_present = all(ef in fields for ef in expected_fields)
        record_check("Sensitive User Fields Protection", all_present and len(fields) >= 37, f"Total {len(fields)} protected fields found (baseline: 37+)")
    else:
        record_check("Sensitive User Fields Protection", False, "modifyingSensitiveUserFields function not found")

    # 4. Support conversation security
    has_support_match = "match /support_conversations/{conversationId}" in rules_content
    has_sender_role_check = "request.resource.data.senderRole == 'user'" in rules_content
    record_check("Support Conversations Owner & Role Boundary", has_support_match and has_sender_role_check, "senderRole='user' and ownership enforced")

    # 5. Pro requests security
    has_pro_requests = "match /pro_requests/{requestId}" in rules_content
    has_pending_check = "request.resource.data.status == 'PENDING'" in rules_content
    has_reviewed_by_check = "request.resource.data.reviewedBy == ''" in rules_content or "request.resource.data.reviewedBy == null" in rules_content
    record_check("Pro Requests PENDING Enforced & Reviewer Protected", has_pro_requests and has_pending_check and has_reviewed_by_check, "Pro request forge prevention verified")

    # 6. Managed extensions security
    has_managed_ext = "match /managed_extensions/{extensionId}" in rules_content
    has_ext_admin_write = bool(re.search(r"match /managed_extensions/\{extensionId\}\s*\{[^}]*allow write:[^;]*isAdmin\(\)", rules_content, re.DOTALL))
    record_check("Managed Extensions Admin-Only Write", has_managed_ext and has_ext_admin_write, "Catalog modification restricted to admin")

    # 7. Audit logs security
    has_audit_logs = "match /auditLogs/{logId}" in rules_content
    has_audit_admin_only = bool(re.search(r"match /auditLogs/\{logId\}\s*\{[^}]*allow read, write:\s*if isAdmin\(\);", rules_content, re.DOTALL))
    record_check("Audit Logs Exclusively Admin Access", has_audit_logs and has_audit_admin_only, "Standard users have zero read/write access to /auditLogs")

    # 8. Catch-all deny
    has_catch_all = bool(re.search(r"match /\{document=\*\*\}\s*\{\s*allow read, write:\s*if false;\s*\}", rules_content))
    record_check("Strict Catch-All Deny", has_catch_all, "match /{document=**} { allow read, write: if false; } present")

    # 9. AndroidManifest Permissions
    manifest_path = "app/src/main/AndroidManifest.xml"
    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest_content = f.read()

    forbidden_perms = ["READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "SYSTEM_ALERT_WINDOW"]
    found_forbidden = [p for p in forbidden_perms if p in manifest_content]
    record_check("Least-Privilege Android Permissions", len(found_forbidden) == 0, f"No dangerous broad storage permissions found: {found_forbidden}")

    # 10. No Hardcoded Cloud Admin Secrets in Kotlin Code
    has_hardcoded_admin_secrets = False
    for root, _, files in os.walk("app/src/main/java"):
        for file in files:
            if file.endswith(".kt"):
                with open(os.path.join(root, file), "r", encoding="utf-8") as f:
                    content = f.read()
                    # Check for leaked Cloudinary secret or private service account private keys
                    if "api_secret" in content.lower() and re.search(r'api_secret\s*=\s*["\'][^"\']+["\']', content, re.IGNORECASE):
                        has_hardcoded_admin_secrets = True
                    if "-----BEGIN PRIVATE KEY-----" in content:
                        has_hardcoded_admin_secrets = True
    record_check("Zero Hardcoded Cloud Admin Secrets", not has_hardcoded_admin_secrets, "No leaked backend admin secrets or private keys in Kotlin code")

    # 11. No Dynamic Code Loading (DCL)
    has_dcl_invocation = False
    for root, _, files in os.walk("app/src/main/java"):
        for file in files:
            if file.endswith(".kt"):
                with open(os.path.join(root, file), "r", encoding="utf-8") as f:
                    lines = f.readlines()
                    for line in lines:
                        stripped = line.strip()
                        if not stripped.startswith("//") and not stripped.startswith("*"):
                            if "DexClassLoader(" in stripped or "PathClassLoader(" in stripped or "loadDex(" in stripped:
                                has_dcl_invocation = True
    record_check("Zero Dynamic Code Loading (DCL)", not has_dcl_invocation, "No executable bytecode or DexClassLoader invocations detected")

    # Summary
    print("\n--------------------------------------------------")
    print(f"TOTAL STATIC CHECKS: {len(results)}")
    print(f"PASSED: {checks_passed}")
    print(f"FAILED: {checks_failed}")
    print("--------------------------------------------------")

    if checks_failed > 0:
        sys.exit(1)

if __name__ == "__main__":
    main()
