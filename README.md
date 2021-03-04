# react-native-fin-inc-id-reader

## Install

`npm i --save react-native-fin-inc-id-reader`

### For Android
* edit AndroidManifest.xml
> in Permission Block
```xml
<uses-permission android:name="android.permission.NFC" />
```
> Add the following Intent in the <activity> block
```xml
<intent-filter>
    <action android:name="android.nfc.action.NDEF_DISCOVERED"/>
    <category android:name="android.intent.category.DEFAULT"/>
</intent-filter>

<intent-filter>
    <action android:name="android.nfc.action.TECH_DISCOVERED"/>
</intent-filter>

<meta-data android:name="android.nfc.action.TECH_DISCOVERED" android:resource="@xml/nfc_tech_filter" />
```
* Create the file `android/src/main/res/xml/nfc_tech_filter.xml` and add the following
```xml
<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <tech-list>
        <tech>android.nfc.tech.IsoDep</tech>
    </tech-list>
</resources>
```

## Usage
```javascript
import EIDReader from 'react-native-fin-inc-id-reader'

async scanId(){
    const data = await EIDReader.scan({
      documentNumber: 'Get from MRZ',
      dateOfBirth: 'yymmdd',
      dateOfExpiry: 'yymmdd'
    })
    console.log(data)
}
```

### For IOS
* Enable NFC under Capability
* edit Info.plist
> Add the following Intent in the <activity> block
```xml
<key>NFCReaderUsageDescription</key>
<string>YOUR_PRIVACY_DESCRIPTION</string>
<key>com.apple.developer.nfc.readersession.iso7816.select-identifiers</key>
<array>
    <string>A0000002471001</string>
    <string>A000000003101001</string>
    <string>A000000003101002</string>
    <string>A0000000041010</string>
    <string>A0000000042010</string>
    <string>A0000000044010</string>
    <string>44464D46412E44466172653234313031</string>
    <string>D2760000850100</string>
    <string>D2760000850101</string>
    <string>00000000000000</string>
    <string>E80704007F00070302</string>
    <string>A000000167455349474E</string>
    <string>A0000002480100</string>
    <string>A0000002480200</string>
    <string>A0000002480300</string>
    <string>A00000045645444C2D3031</string>
</array>
```

## Usage
```javascript
import EIDReader from 'react-native-fin-inc-id-reader'

async scanId(){
    const data = await EIDReader.scan({
      documentNumber: 'Get from MRZ',
      dateOfBirth: 'yymmdd',
      dateOfExpiry: 'yymmdd'
    })
    console.log(data)
}
```