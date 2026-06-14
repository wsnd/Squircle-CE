# License Compliance Guide for Squircle CE

This document explains the license files created for Squircle CE and how to use them for app store submission and compliance.

---

## 📄 License Files Overview

### 1. **LICENSE** (Existing)
- **Location:** Project root
- **Purpose:** Main project license (Apache 2.0)
- **Use when:** 
  - Someone wants to use your code
  - Defining overall project terms
  - Standard open source distribution

### 2. **NOTICE** (New ✨)
- **Location:** Project root
- **Purpose:** Attribution notices required by Apache 2.0
- **Contains:**
  - Copyright attributions
  - Sora Editor LGPL notice
  - Python runtime attribution
  - Links to source repositories
- **Use when:**
  - Distributing the application
  - App store submission (include in app package)
  - Required by Apache 2.0 Section 4(d)

### 3. **THIRD-PARTY-LICENSES** (New ✨)
- **Location:** Project root
- **Purpose:** Complete list of all third-party libraries and their licenses
- **Contains:**
  - All dependencies with licenses
  - Repository links
  - Copyright holders
  - Special notes about LGPL components
  - License summary table
- **Use when:**
  - Legal audit or compliance review
  - Developer documentation
  - Complete transparency about dependencies

### 4. **OPEN-SOURCE-LICENSES.md** (New ✨)
- **Location:** Project root
- **Purpose:** User-friendly, readable license summary
- **Contains:**
  - Simplified explanations
  - User rights overview
  - Contribution guidelines
  - Contact information
- **Use when:**
  - Displaying in-app "About" or "Licenses" screen
  - GitHub repository README section
  - User-facing documentation
  - Mobile app settings page

### 5. **APP-STORE-LICENSE-NOTICE.md** (New ✨)
- **Location:** Project root
- **Purpose:** Template for app store submissions
- **Contains:**
  - App store description templates
  - FAQ for review teams
  - Privacy policy guidance
  - Permission explanations
  - In-app content templates
- **Use when:**
  - Submitting to Google Play Store
  - Submitting to F-Droid
  - Any app store submission
  - Preparing marketing materials

---

## 🎯 Usage Scenarios

### Scenario 1: App Store Submission (Google Play / F-Droid)

**Files to include:**
1. ✅ Copy `NOTICE` into app assets or include reference in description
2. ✅ Use `APP-STORE-LICENSE-NOTICE.md` for:
   - App description text
   - Data safety section answers
   - Permission justifications
   - Review team FAQs

**In your app:**
```
Settings → About → Open Source Licenses
```
Display content from `OPEN-SOURCE-LICENSES.md`

**In app store listing:**
Use template from `APP-STORE-LICENSE-NOTICE.md` → "Full Description - Open Source Section"

---

### Scenario 2: GitHub Repository

**Already done!** ✅

The `README.md` has been updated to include:
- Link to LICENSE
- Link to NOTICE
- Link to THIRD-PARTY-LICENSES
- Link to OPEN-SOURCE-LICENSES.md

Visitors can easily find all license information.

---

### Scenario 3: In-App License Screen

**Implementation suggestion:**

Create a screen/dialog that displays:

```kotlin
// Pseudo-code for Android Compose
@Composable
fun LicenseScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Open Source Licenses", style = MaterialTheme.typography.h5)
        
        Spacer(Modifier.height(16.dp))
        
        // Load and display OPEN-SOURCE-LICENSES.md
        HtmlText(text = loadMarkdownFromAssets("OPEN-SOURCE-LICENSES.md"))
        
        Spacer(Modifier.height(16.dp))
        
        TextButton(onClick = { /* Open full THIRD-PARTY-LICENSES */ }) {
            Text("View Complete Licenses")
        }
    }
}
```

---

### Scenario 4: Legal Compliance Audit

**Provide these files:**
1. ✅ LICENSE - Main project license
2. ✅ NOTICE - Attribution notices
3. ✅ THIRD-PARTY-LICENSES - Complete dependency list
4. ✅ Build scripts showing dynamic linking (for LGPL compliance)

**Key points to emphasize:**
- Sora Editor is dynamically linked via Maven (not modified)
- Users can replace the library (LGPL requirement satisfied)
- App code remains separate from LGPL components
- All attributions are properly maintained

---

### Scenario 5: Commercial Distribution / Paid Version

**Same requirements as free version:**
- Keep all license files
- Maintain attributions
- Include NOTICE in distribution
- Provide access to source code (GitHub link)

**Additional considerations:**
- Clearly state it's based on open source Squircle CE
- Don't claim exclusive ownership
- Honor user rights under licenses

---

## ⚖️ Compliance Checklist

### For Apache 2.0 (Squircle CE base):
- ✅ Include LICENSE file
- ✅ Include NOTICE file
- ✅ State changes made to files (if any)
- ✅ Retain copyright notices

### For LGPL v2.1 (Sora Editor):
- ✅ Dynamic linking (via Maven/AAR)
- ✅ Allow user to replace library
- ✅ Provide build scripts
- ✅ Include LGPL license text
- ✅ State user's rights
- ❌ NOT required: Open source your app code

### For PSF License (Python):
- ✅ Include Python copyright
- ✅ Acknowledge Python Software Foundation
- ✅ Python is open source, no restrictions

### For MIT/BSD libraries:
- ✅ Include copyright notices
- ✅ Include license text
- These are very permissive, minimal requirements

---

## 🚀 Quick Start Guide

### If you're releasing the app TODAY:

1. **Include in APK/App Bundle:**
   - NOTICE file (in assets folder)
   - Reference to OPEN-SOURCE-LICENSES.md

2. **In app store description:**
   - Copy text from APP-STORE-LICENSE-NOTICE.md
   - Mention "open source"
   - Link to GitHub repository

3. **In the app itself:**
   - Add "About" or "Licenses" screen
   - Display content from OPEN-SOURCE-LICENSES.md
   - Link to full THIRD-PARTY-LICENSES

4. **On GitHub:**
   - ✅ Already done! README.md updated

---

## 📱 Android Implementation Example

### Option A: Simple WebView

```kotlin
class LicenseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)
        
        // Load OPEN-SOURCE-LICENSES.md from assets
        val markdown = assets.open("OPEN-SOURCE-LICENSES.md")
            .bufferedReader().use { it.readText() }
        
        // Convert markdown to HTML and display
        webView.loadDataWithBaseURL(null, markdownToHtml(markdown), 
            "text/html", "UTF-8", null)
    }
}
```

### Option B: Compose with Markdown Library

```kotlin
@Composable
fun LicenseScreen() {
    val context = LocalContext.current
    val markdown = remember {
        context.assets.open("OPEN-SOURCE-LICENSES.md")
            .bufferedReader().use { it.readText() }
    }
    
    MarkdownText(
        markdown = markdown,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    )
}
```

### Option C: Simple Text Display

```kotlin
@Composable
fun LicenseScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Open Source Licenses", style = text20Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This app uses Sora Editor (LGPL v2.1), " +
                   "Python Runtime, and other open source libraries.\n\n" +
                   "View full license information at:\n" +
                   "https://github.com/black-squircle/squircle-ce",
            style = text14Regular
        )
    }
}
```

---

## 🔗 Useful Links

- **Apache 2.0 License:** https://www.apache.org/licenses/LICENSE-2.0
- **LGPL v2.1 License:** https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
- **Sora Editor:** https://github.com/Rosemoe/sora-editor
- **Python License:** https://docs.python.org/3/license.html
- **FSF LGPL FAQ:** https://www.gnu.org/licenses/lgpl-faq.html
- **ChooseALicense.com:** https://choosealicense.com/

---

## ❓ Common Questions

**Q: Do I need to open source my modifications?**
A: No. You only need to open source modifications to LGPL libraries themselves (like Sora Editor). Your app code can remain closed.

**Q: Can I sell this app?**
A: Yes. Apache 2.0 and LGPL both permit commercial use. Just maintain attributions.

**Q: What if I modify Sora Editor?**
A: You must open source ONLY the Sora Editor modifications, not your entire app.

**Q: Is this legal advice?**
A: No. This is a practical guide. For legal certainty, consult an attorney specializing in open source licensing.

---

## 📞 Support

If you have questions about license compliance:
1. Check this guide first
2. Review the actual license texts
3. Consult the FSF LGPL FAQ
4. Consider professional legal advice for complex situations

---

**Last updated:** 2024  
**Maintained by:** Squircle CE contributors
