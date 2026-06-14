# App Store License Notice Template

## For Google Play Store / App Store Description

---

### Short Description (for app store listing):

```
A powerful code editor for Android with Python support. 
Open source and built with love. ❤️
```

---

### Full Description - Open Source Section:

```
📱 OPEN SOURCE

Squircle CE is an open source application built with the following technologies:

• Licensed under Apache License 2.0
• Uses Sora Editor (LGPL v2.1) - https://github.com/Rosemoe/sora-editor
• Includes Python runtime - https://www.python.org/
• Built with modern Android technologies (Kotlin, Jetpack Compose, etc.)

Source code available at: https://github.com/black-squircle/squircle-ce

This app respects your freedom to inspect, modify, and redistribute the code
under the terms of the applicable open source licenses.

🔒 PRIVACY

We respect your privacy. This app:
• Does not collect personal data
• Works offline
• Does not require internet permission for core functionality
• Stores all data locally on your device

For full privacy policy, see: [你的隐私政策链接]
```

---

### Additional Notes for App Store Submission:

**For "Data Safety" section:**
- ✅ No data collected
- ✅ No data shared with third parties
- ✅ All data stored locally
- ✅ Optional cloud sync (if implemented) requires user consent

**For "Permissions" explanation:**
- MANAGE_EXTERNAL_STORAGE: Required for file editing functionality (core feature)
- INTERNET: Only used for optional features (Git remote, FTP/SFTP, etc.)
- ROOT access: Optional, only if user explicitly enables it

---

### FAQ for App Review Team:

**Q: Is this app compliant with open source licenses?**
A: Yes. We include proper attribution in the app and repository:
   - LICENSE file (Apache 2.0)
   - NOTICE file (Attribution notices)
   - THIRD-PARTY-LICENSES file (Complete license list)
   - In-app "About" page with license information

**Q: Does the app use any restricted libraries?**
A: The app uses Sora Editor under LGPL v2.1, which permits:
   - Dynamic linking (used via Maven dependency)
   - User's right to replace the library
   - Our app code remains proprietary/closed source
   This is fully compliant with LGPL requirements.

**Q: Can users inspect the source code?**
A: Yes. Full source code is available on GitHub:
   https://github.com/black-squircle/squircle-ce

**Q: Are there any GPL components that would require open sourcing the app?**
A: No. While we use LGPL (Sora Editor), this does NOT require our app to be
open source. LGPL only requires:
   - Allow library replacement (we do)
   - Provide build scripts (we do)
   - If we modified the library, share those changes (we didn't modify it)
Our app code can remain closed source while being fully compliant.

---

### In-App "About" Page Content:

Create a screen or dialog with:

```
About Squircle CE

Version: [版本号]
Build: [构建号]

This app is licensed under Apache License 2.0.

It uses the following open source libraries:
• Sora Editor - LGPL v2.1
• Python Runtime - PSF License
• And many others...

View full source code:
https://github.com/black-squircle/squircle-ce

View complete license information:
Settings → About → Open Source Licenses

© 2024 Black Squircle UI
```

---

### Important Reminders:

✅ DO:
- Include links to source code in app description
- Mention "open source" as a feature
- Provide license information in the app
- Be transparent about permissions and data usage
- Link to privacy policy

❌ DON'T:
- Claim to be "completely original" without attribution
- Remove copyright notices from dependencies
- Misrepresent the nature of open source components
- Hide or obscure license information

---

This template helps ensure compliance with app store requirements while
properly acknowledging the open source nature of the project.
