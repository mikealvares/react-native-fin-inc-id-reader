package com.reactlibrary;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import net.sf.scuba.smartcards.CardFileInputStream;
import net.sf.scuba.smartcards.CardService;

import org.jmrtd.BACKey;
import org.jmrtd.BACKeySpec;
import org.jmrtd.ChipAuthenticationResult;
import org.jmrtd.PassportService;
import org.jmrtd.lds.COMFile;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.DG1File;
import org.jmrtd.lds.DG2File;
import org.jmrtd.lds.DG3File;
import org.jmrtd.lds.DG7File;
import org.jmrtd.lds.DG11File;
import org.jmrtd.lds.DG14File;
import org.jmrtd.lds.FaceImageInfo;
import org.jmrtd.lds.FaceInfo;
import org.jmrtd.lds.DisplayedImageInfo;
import org.jmrtd.lds.LDS;
import org.jmrtd.lds.MRZInfo;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SODFile;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinIncIdReaderModule extends ReactContextBaseJavaModule implements ActivityEventListener,LifecycleEventListener {
    private static final String LOG_TAG = "FinIncNfc";
    private final ReactApplicationContext reactContext;
    private Promise scanPromise;
    private ReadableMap opts;
    private static final String JPEG_DATA_URI_PREFIX = "data:image/jpeg;base64,";
    private static final int SCAN_REQUEST_CODE = 8735738;
    private static final String E_NOT_SUPPORTED = "E_NOT_SUPPORTED";
    private static final String E_NOT_ENABLED = "E_NOT_ENABLED";
    private static final String E_SCAN_CANCELED = "E_SCAN_CANCELED";
    private static final String E_SCAN_FAILED = "E_SCAN_FAILED";
    private static final String E_SCAN_FAILED_DISCONNECT = "E_SCAN_FAILED_DISCONNECT";
    private static final String E_ONE_REQ_AT_A_TIME = "E_ONE_REQ_AT_A_TIME";
    private static final String KEY_IS_SUPPORTED = "isSupported";
    private static final String PARAM_DOC_NUM = "documentNumber";
    private static final String PARAM_DOB = "dateOfBirth";
    private static final String PARAM_DOE = "dateOfExpiry";



    public FinIncIdReaderModule(ReactApplicationContext reactContext) {
        super(reactContext);
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        reactContext.addLifecycleEventListener(this);
        reactContext.addActivityEventListener(this);
        this.reactContext = reactContext;
    }
    public void onActivityResult(int requestCode, int resultCode, Intent data) {}
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {}
    public void onNewIntent(Intent intent) {
        if (scanPromise == null) return;
        if (!NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) return;
        Tag tag = intent.getExtras().getParcelable(NfcAdapter.EXTRA_TAG);
        if (!Arrays.asList(tag.getTechList()).contains(IsoDep.class.getName())) return;
        BACKeySpec bacKey = new BACKey(
                opts.getString(PARAM_DOC_NUM),
                opts.getString(PARAM_DOB),
                opts.getString(PARAM_DOE)
        );
        new ReadTask(IsoDep.get(tag), bacKey).execute();
    }
    @Override
    public String getName() {return "FinIncIdReader";}
    @Override
    public void onHostDestroy() {resetState();}
    @Override
    public void onHostResume() {
        NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this.reactContext);
        if (mNfcAdapter == null) return;
        Activity activity = getCurrentActivity();
        Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getCurrentActivity(), 0, intent, 0);//PendingIntent.FLAG_UPDATE_CURRENT);
        String[][] filter = new String[][] { new String[] { IsoDep.class.getName()  } };
        mNfcAdapter.enableForegroundDispatch(getCurrentActivity(), pendingIntent, null, filter);
    }
    @Override
    public void onHostPause() {
        NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this.reactContext);
        if (mNfcAdapter == null) return;
        mNfcAdapter.disableForegroundDispatch(getCurrentActivity());
    }
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        boolean hasNFC = reactContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
        constants.put(KEY_IS_SUPPORTED, hasNFC);
        return constants;
    }
    private void resetState() {scanPromise = null;opts = null;}
    private static String exceptionStack(Throwable exception) {
        StringBuilder s = new StringBuilder();
        String exceptionMsg = exception.getMessage();
        if (exceptionMsg != null) {
            s.append(exceptionMsg);
            s.append(" - ");
        }
        s.append(exception.getClass().getSimpleName());
        StackTraceElement[] stack = exception.getStackTrace();
        if (stack.length > 0) {
            int count = 3;
            boolean first = true;
            boolean skip = false;
            String file = "";
            s.append(" (");
            for (StackTraceElement element : stack) {
                if (count > 0 && element.getClassName().startsWith("io.tradle")) {
                    if (!first) {
                        s.append(" < ");
                    } else {
                        first = false;
                    }
                    if (skip) {
                        s.append("... < ");
                        skip = false;
                    }
                    if (file.equals(element.getFileName())) {
                        s.append("*");
                    } else {
                        file = element.getFileName();
                        s.append(file.substring(0, file.length() - 5)); // remove ".java"
                        count -= 1;
                    }
                    s.append(":").append(element.getLineNumber());
                } else {
                    skip = true;
                }
            }
            if (skip) {
                if (!first) {
                    s.append(" < ");
                }
                s.append("...");
            }
            s.append(")");
        }
        return s.toString();
    }
    private static String toBase64(final Bitmap bitmap, final int quality) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return JPEG_DATA_URI_PREFIX + Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }
    @ReactMethod
    public void cancel(final Promise promise) {
        if (scanPromise != null) {
            scanPromise.reject(E_SCAN_CANCELED, "canceled");
        }
        resetState();
        promise.resolve(null);
    }
    @ReactMethod
    public void scan(final ReadableMap opts, final Promise promise) {
        NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this.reactContext);
        if (mNfcAdapter == null) {
            promise.reject(E_NOT_SUPPORTED, "NFC chip reading not supported");
            return;
        }
        if (!mNfcAdapter.isEnabled()) {
            promise.reject(E_NOT_ENABLED, "NFC chip reading not enabled");
            return;
        }
        if (scanPromise != null) {
            promise.reject(E_ONE_REQ_AT_A_TIME, "Already running a scan");
            return;
        }
        this.opts = opts;
        this.scanPromise = promise;
    }
    private class ReadTask extends AsyncTask<Void, Void, Exception> {
        private IsoDep isoDep;
        private BACKeySpec bacKey;

        public ReadTask(IsoDep isoDep, BACKeySpec bacKey) {
            this.isoDep = isoDep;
            this.bacKey = bacKey;
        }

        private COMFile comFile;
        private SODFile sodFile;
        private DG1File dg1File;
        private DG2File dg2File;
        private DG3File dg3File;
        private DG7File dg7File;
        private DG11File dg11File;
        private DG14File dg14File;

        private Bitmap bitmap;
        private Bitmap signature;

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                CardService cardService = CardService.getInstance(isoDep);
                cardService.open();
                PassportService service = new PassportService(cardService);
                service.open();
                boolean paceSucceeded = false;
                try {
                    CardAccessFile cardAccessFile = new CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS));
                    Collection<PACEInfo> paceInfos = cardAccessFile.getPACEInfos();
                    if (paceInfos != null && paceInfos.size() > 0) {
                        PACEInfo paceInfo = paceInfos.iterator().next();
                        service.doPACE(bacKey, paceInfo.getObjectIdentifier(), PACEInfo.toParameterSpec(paceInfo.getParameterId()));
                        paceSucceeded = true;
                    } else {
                        paceSucceeded = true;
                    }
                } catch (Exception e) {
                    Log.w(LOG_TAG, e);
                }
                service.sendSelectApplet(paceSucceeded);
                if (!paceSucceeded) {
                    try {
                        service.getInputStream(PassportService.EF_COM).read();
                    } catch (Exception e) {
                        service.doBAC(bacKey);
                    }
                }
                LDS lds = new LDS();
                /* MRZ */
                CardFileInputStream dg1In = service.getInputStream(PassportService.EF_DG1);
                lds.add(PassportService.EF_DG1, dg1In, dg1In.getLength());
                dg1File = lds.getDG1File();
                /* FACE */
                CardFileInputStream dg2In = service.getInputStream(PassportService.EF_DG2);
                lds.add(PassportService.EF_DG2, dg2In, dg2In.getLength());
                dg2File = lds.getDG2File();
                /* FingerPrint */
                /*CardFileInputStream dg3In = service.getInputStream(PassportService.EF_DG3);
                lds.add(PassportService.EF_DG3, dg3In, dg3In.getLength());
                dg3File = lds.getDG3File();*/
                /* Images */
                CardFileInputStream dg7In = service.getInputStream(PassportService.EF_DG7);
                lds.add(PassportService.EF_DG7, dg7In, dg7In.getLength());
                dg7File = lds.getDG7File();
                /* Extra Information */
                CardFileInputStream dg11In = service.getInputStream(PassportService.EF_DG11);
                lds.add(PassportService.EF_DG11, dg11In, dg11In.getLength());
                dg11File = lds.getDG11File();
                /* Security */
                /*CardFileInputStream dg14In = service.getInputStream(PassportService.EF_DG14);
                lds.add(PassportService.EF_DG14, dg14In, dg14In.getLength());
                dg14File = lds.getDG14File();*/

                /*Map<BigInteger, PublicKey>keyInfo = dg14File.getChipAuthenticationPublicKeyInfos();
                Map.Entry<BigInteger, PublicKey> entry = keyInfo.entrySet().iterator().next();
                ChipAuthenticationResult caResult = doCA(ps, entry.getKey(), entry.getValue());*/


                List<FaceImageInfo> allFaceImageInfos = new ArrayList<>();
                List<FaceInfo> faceInfos = dg2File.getFaceInfos();
                for (FaceInfo faceInfo : faceInfos) {
                    allFaceImageInfos.addAll(faceInfo.getFaceImageInfos());
                }
                if (!allFaceImageInfos.isEmpty()) {
                    FaceImageInfo faceImageInfo = allFaceImageInfos.iterator().next();
                    int imageLength = faceImageInfo.getImageLength();
                    DataInputStream dataInputStream = new DataInputStream(faceImageInfo.getImageInputStream());
                    byte[] buffer = new byte[imageLength];
                    dataInputStream.readFully(buffer, 0, imageLength);
                    InputStream inputStream = new ByteArrayInputStream(buffer, 0, imageLength);
                    bitmap = ImageUtil.decodeImage(reactContext, faceImageInfo.getMimeType(), inputStream);
                }
                List <DisplayedImageInfo> signatureImageInfos = dg7File.getImages();
                if (!signatureImageInfos.isEmpty()) {
                    int imageLength = signatureImageInfos.iterator().next().getImageLength();
                    DataInputStream dataInputStream = new DataInputStream(signatureImageInfos.iterator().next().getImageInputStream());
                    byte[] buffer = new byte[imageLength];
                    dataInputStream.readFully(buffer, 0, imageLength);
                    InputStream inputStream = new ByteArrayInputStream(buffer, 0, imageLength);
                    signature = ImageUtil.decodeImage(reactContext, signatureImageInfos.iterator().next().getMimeType(), inputStream);
                }
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (scanPromise == null) return;
            if (result != null) {
                Log.w(LOG_TAG, exceptionStack(result));
                if (result instanceof IOException) {
                    scanPromise.reject(E_SCAN_FAILED_DISCONNECT, "Lost connection to chip on card");
                } else {
                    scanPromise.reject(E_SCAN_FAILED, result);
                }
                resetState();
                return;
            }
            MRZInfo mrzInfo = dg1File.getMRZInfo();
            int quality = 100;
            if (opts.hasKey("quality")) {
                quality = (int)(opts.getDouble("quality") * 100);
            }
            String base64 = toBase64(bitmap, quality);
            WritableMap photo = Arguments.createMap();
            photo.putString("base64", base64);
            photo.putInt("width", bitmap.getWidth());
            photo.putInt("height", bitmap.getHeight());

            WritableMap sign = Arguments.createMap();
            base64 = toBase64(signature, quality);
            sign.putString("base64", base64);
            sign.putInt("width", signature.getWidth());
            sign.putInt("height", signature.getHeight());

//            String firstName = mrzInfo.getSecondaryIdentifier().replace("<", "");
//            String lastName = mrzInfo.getPrimaryIdentifier().replace("<", " ");
            WritableMap passport = Arguments.createMap();
            passport.putMap("photo", photo);
            passport.putMap("sign", sign);
            passport.putString("dob",mrzInfo.getDateOfBirth());
            passport.putString("exp",mrzInfo.getDateOfExpiry());
            passport.putInt("docType",mrzInfo.getDocumentType());
            passport.putString("sex",mrzInfo.getGender().toString());
            passport.putString("issued",mrzInfo.getIssuingState());
            passport.putString("nationality",mrzInfo.getNationality());
            passport.putString("opt1",mrzInfo.getOptionalData1());
            passport.putString("opt2",mrzInfo.getOptionalData2());
            passport.putString("idNum",mrzInfo.getPersonalNumber());
            passport.putString("primaryId",mrzInfo.getPrimaryIdentifier());
            passport.putString("secId",mrzInfo.getSecondaryIdentifier());
            passport.putString("secIdComp",mrzInfo.getSecondaryIdentifierComponents().toString());

            passport.putString("d11cinf",dg11File.getCustodyInformation());
            passport.putString("fulldob",dg11File.getFullDateOfBirth().toString());
            passport.putString("fullname",dg11File.getNameOfHolder());
            passport.putString("otherName",dg11File.getOtherNames().toString());
            passport.putString("otherTdn",dg11File.getOtherValidTDNumbers().toString());
            passport.putString("addr",dg11File.getPermanentAddress().toString());
            passport.putString("pno",dg11File.getPersonalNumber());
            passport.putString("summary",dg11File.getPersonalSummary());
            passport.putString("placeOfbirth",dg11File.getPlaceOfBirth().toString());
            passport.putString("profession",dg11File.getProfession());
            passport.putString("phone",dg11File.getTelephone());
            passport.putString("title",dg11File.getTitle());
            passport.putString("tags",dg11File.getTagPresenceList().toString());
            scanPromise.resolve(passport);
            resetState();
        }
    }
}