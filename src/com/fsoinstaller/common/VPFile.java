
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 
 * 
 * @author Ryan Sakowski
 */
public class VPFile implements Closeable {
    
    /** First 4 bytes of a VP file read as a 32-bit little-endian integer. */
    private static final int VP_HEADER_ID = 0x50565056;
    
    /** Size of the buffer used for various tasks. */
    private static final int BUFFER_SIZE = 16384;
    
    /** Name of the charset used to read and write strings. */
    private static final String VP_CHARSET_NAME = "ISO-8859-1";
    
    /** Regular expression flags to use when matching entry names. */
    private static final int REGEX_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.UNICODE_CASE;
    
    
    private static final int MAX_PATIENCE = 16;
    
    /** Empty string array that's used instead of <code>new String[0]</code>. */
    private static final String[] EMPTY_STRING_ARRAY = {};
    
    
    /** The VP file. */
    private final RandomAccessFile file;
    
    /** The location of the entry list in the VP file. */
    private long entryListOffset;
    
    /** A set of all the folders in this VP file, including those that are empty. */
    private final SortedSet<VPFolder> folderSet;
    
    /** A list of all entries in this VP file. */
    private final List<VPEntry> entryList;
    
    /** If true, this VP file is not open with write permissions. */
    private final boolean readOnly;
    
    /** The InputStream that's open on this VP file. */
    private VPEntryInputStream activeInputStream = null;
    
    /** The OutputStream that's open on this VP file. */
    private VPEntryOutputStream activeOutputStream = null;
    
    /** Tells whether this VP file is open. */
    private boolean isOpen = true;
    
    /** Tells whether this VP file has been modified in some way. */
    private boolean modified = false;
    
    
    
    public VPFile(File filePath, String mode) throws VPFormatException, FileNotFoundException, IOException {
        readOnly = mode.equals("r");
        file = new RandomAccessFile(filePath, mode);
        
        // Make sure that this is a valid VP file that we can read.
        if (file.length() < 16L) {
            throw new VPFormatException(String.format("%s is not at least 16 bytes in size.", filePath.getName()));
        }
        if (readInt() != VP_HEADER_ID) {
            throw new VPFormatException(String.format("%s is not a valid VP file.", filePath.getName()));
        }
        if (readInt() != 2) {
            throw new VPFormatException(String.format("Format of %s is not 2.", filePath.getName()));
        }
        
        entryListOffset = readUnsignedInt();
        int entryCount = readInt();
        // Make sure that the VP file contains the full entry list.
        if (entryListOffset + entryCount * 44L > file.length()) {
            throw new VPFormatException(String.format("%s is corrupted or invalid.", filePath.getName()));
        }
        
        file.seek(entryListOffset);
        // The folders should be sorted, so use a TreeSet.
        folderSet = new TreeSet<VPFolder>();
        entryList = new ArrayList<VPEntry>(4096);
        VPFolder currentFolder = new VPFolder();
        // Add an empty folder to folderSet so that entries can exist in
        // the VP's root without using null as their folder.
        folderSet.add(currentFolder);
        // Time to read in the entries.
        for (long i = 0L; i < entryCount; i++) {
            long offset = readUnsignedInt();
            long size = readUnsignedInt();
            String name = readString();
            int timestamp = readInt();
            
            if (size == 0) {
                if (name.equals("..")) {
                    // backdir
                    currentFolder = currentFolder.getParent();
                } else {
                    currentFolder = new VPFolder(currentFolder, name);
                    folderSet.add(currentFolder);
                }
            } else {
                VPEntry entry = new VPEntry(offset, size, currentFolder, name, timestamp);
                // Make sure that all entries actually exist within this VP.
                if (entry.offset + entry.size > file.length()) {
                    throw new VPFormatException(String.format("%s is corrupted or invalid.", filePath.getName()));
                }
                entryList.add(entry);
            }
        }
        
        // The entries, like the folders, should also be sorted, so sort them now.
        Collections.sort(entryList);
    }
    
    /**
     * Returns a list of all the entries in this VP file.
     * <p>
     * The returned list is a copy of this VP file's entry list; thus, any
     * changes made to the VP file after this method is called are not
     * reflected in the returned list.
     * 
     * @return a copy of this VP file's entry list
     */
    public List<VPEntry> getEntryList() {
        return new ArrayList<VPEntry>(entryList);
    }
    
    /**
     * Returns a list of all the entries in this VP file that match the given
     * regular expression.
     * <p>
     * The returned list is a newly created list that does not share its data
     * with this VP file's entry list. Thus, the returned list will not
     * automatically be updated if this VP file is changed.
     * 
     * @param regex regular expression to match
     * @return a list of entries that match the given regular expression
     */
    public List<VPEntry> getEntryList(String regex) {
        return getEntryList(regex, "");
    }
    
    /**
     * Returns a list of all the entries in this VP file that match the regular
     * expression <code>regex</code> but not <code>regexExclude</code>.
     * <p>
     * The returned list is a newly created list that does not share its data
     * with this VP file's entry list. Thus, the returned list will not
     * automatically be updated if this VP file is changed.
     * 
     * @param regex regular expression to match
     * @param regexExclude regular expression not to match
     * @return a list of entries that match regex but not regexExclude
     */
    public List<VPEntry> getEntryList(String regex, String regexExclude) {
        List<VPEntry> filteredEntryList = new ArrayList(entryList.size());
        // Normalize all slashes to backslashes.
        Pattern pattern = Pattern.compile(regex.replace("/", "\\\\"), REGEX_FLAGS);
        Pattern excludePattern = Pattern.compile(regexExclude.replace("/", "\\\\"), REGEX_FLAGS);
        
        for (VPEntry entry : entryList) {
            String entryString = entry.fullPath;
            if (pattern.matcher(entryString).matches() && !excludePattern.matcher(entryString).matches()) {
                filteredEntryList.add(entry);
            }
        }
        
        Collections.sort(filteredEntryList);
        return filteredEntryList;
    }
    
    /**
     * Returns a set of all the folders in this VP file.
     * <p>
     * The returned set is a copy of this VP file's folder set; thus, any
     * changes made to the VP file after this method is called are not
     * reflected in the returned set.
     * 
     * @return a copy of this VP file's entry set
     */
    public SortedSet<VPFolder> getFolderSet() {
        return new TreeSet(folderSet);
    }
    
    /**
     * Returns a set of all the folders in this VP file that match the given
     * regular expression.
     * <p>
     * The returned set is a newly created set that does not share its data
     * with this VP file's folder set. Thus, the returned set will not
     * automatically be updated if this VP file is changed.
     * 
     * @param regex regular expression to match
     * @return a set of entries that match the given regular expression
     */
    public SortedSet<VPFolder> getFolderSet(String regex) {
        return getFolderSet(regex, "");
    }
    
    /**
     * Returns a set of all the entries in this VP file that match the regular
     * expression <code>regex</code> but not <code>regexExclude</code>.
     * <p>
     * The returned set is a newly created set that does not share its data
     * with this VP file's folder set. Thus, the returned set will not
     * automatically be updated if this VP file is changed.
     * 
     * @param regex regular expression to match
     * @param regexExclude regular expression not to match
     * @return a set of folders that match regex but not regexExclude
     */
    public SortedSet<VPFolder> getFolderSet(String regex, String regexExclude) {
        SortedSet<VPFolder> filteredFolderSet = new TreeSet();
        // Normalize all slashes to backslashes.
        Pattern pattern = Pattern.compile(regex.replace("/", "\\\\"), REGEX_FLAGS);
        Pattern excludePattern = Pattern.compile(regexExclude.replace("/", "\\\\"), REGEX_FLAGS);
        
        for (VPFolder folder : folderSet) {
            String folderString = folder.path;
            if (pattern.matcher(folderString).matches() && !excludePattern.matcher(folderString).matches()) {
                filteredFolderSet.add(folder);
            }
        }
        
        return filteredFolderSet;
    }
    
    /**
     * Returns the entry that matches the given string.
     * 
     * @param entryName the name and path of the entry
     * @return the VP entry
     * @throws VPEntryNotFoundException if the given entry name does not exist
     * @throws IOException if an I/O error occurs
     */
    public VPEntry getEntry(String entryName) throws IOException {
        if (!isOpen) {
            throw new IOException("VP file has already been closed.");
        }
        
        // Normalize all slashes to backslashes.
        entryName = entryName.replace('/', '\\');
        VPEntry entry = null;
        for (VPEntry e : entryList) {
            if (e.fullPath.equalsIgnoreCase(entryName)) {
                entry = e;
            } else if (entry != null) {
                break;
            }
        }
        
        if (entry == null) {
            throw new VPEntryNotFoundException(String.format("%s does not exist in VP file.", entryName));
        }
        
        return entry;
    }
    
    /**
     * Opens an input stream on the given entry.
     * <p>
     * If a stream is already open on this VP file, then an IOException will
     * be thrown.
     * 
     * @param entry entry to open
     * @return a new input stream
     * @throws VPEntryNotFoundException if the given entry doesn't exist
     * @throws IOException if an I/O error occurs
     */
    public VPEntryInputStream openEntry(VPEntry entry) throws IOException {
        if (!isOpen) {
            throw new IOException("VP file has already been closed.");
        }
        if (activeInputStream != null || activeOutputStream != null) {
            throw new IOException("Cannot open an input stream on this VP file while a stream is already open on it.");
        }
        int entryIndex = Collections.binarySearch(entryList, entry);
        if (entryIndex < 0) {
            throw new VPEntryNotFoundException(String.format("%s does not exist in VP file.", entry));
        }
        
        activeInputStream = new VPEntryInputStream(entryList.get(entryIndex));
        return activeInputStream;
    }
    
    /**
     * Opens an input stream on the given entry.
     * <p>
     * If a stream is already open on this VP file, then an IOException will
     * be thrown.
     * 
     * @param entryName the name and path of the entry
     * @return a new input stream
     * @throws VPEntryNotFoundException if the given entry doesn't exist
     * @throws IOException if an I/O error occurs
     */
    public VPEntryInputStream openEntry(String entryName) throws IOException {
        return openEntry(getEntry(entryName));
    }
    
    /**
     * Extracts all entries and folders in this VP file to the given
     * destination. All folders in this VP, even those that are empty, will be
     * created.
     * 
     * @param destFolder folder to extract to
     * @throws IOException if an I/O error occurs
     */
    public void extractAll(File destFolder) throws IOException {
        if (!isOpen) {
            throw new IOException("VP file has already been closed.");
        }
        if (activeInputStream != null || activeOutputStream != null) {
            throw new IOException("Cannot extract this VP file while a stream is already open on it.");
        }
        
        // Create all the folders first.
        for (VPFolder vpFolder : folderSet) {
            // Replace backslashes with the file system's separator.
            String vpFolderString = vpFolder.path.replace('\\', File.separatorChar);
            File folder = new File(destFolder, vpFolderString);
            folder.mkdirs();
        }
        extract(destFolder, ".*", "");
    }
    
    /**
     * Extracts all entries whose full paths and names match the given regular
     * expression to the given destination. Folders will be created as
     * necessary.
     * 
     * @param destFolder folder to extract to
     * @param regex regular expression to match
     * @throws IOException if an I/O error occurs
     */
    public void extract(File destFolder, String regex) throws IOException {
        extract(destFolder, regex, "");
    }
    
    /**
     * Extracts all entries whose full paths and names match <code>regex</code>,
     * but not <code>regexExclude</code>, to the given destination. Folders will
     * be created as necessary.
     * 
     * @param destFolder folder to extract to
     * @param regex regular expression to match
     * @param regexExclude regular expression not to match
     * @throws IOException if an I/O error occurs
     */
    public void extract(File destFolder, String regex, String regexExclude) throws IOException {
        if (!isOpen) {
            throw new IOException("VP file has already been closed.");
        }
        if (activeInputStream != null || activeOutputStream != null) {
            throw new IOException("Cannot extract this VP file while a stream is already open on it.");
        }
        
        // Normalize all slashes to backslashes.
        Pattern pattern = Pattern.compile(regex.replace("/", "\\\\"), REGEX_FLAGS);
        Pattern excludePattern = Pattern.compile(regexExclude.replace("/", "\\\\"), REGEX_FLAGS);
        
        FileChannel vpChannel = file.getChannel();
        
        // Now extract the files.
        for (VPEntry entry : entryList) {
            String entryString = entry.fullPath;
            if (!pattern.matcher(entryString).matches()
                    || excludePattern.matcher(entryString).matches()) {
                continue;
            }
            
            // Replace backslashes with the file system's separator.
            entryString = entryString.replace('\\', File.separatorChar);
            File destFile = new File(destFolder, entryString);
            destFile.getParentFile().mkdirs();
            
            WritableByteChannel destChannel = null;
            try {
                destChannel = new FileOutputStream(destFile).getChannel();
                
                long currentPosition = entry.offset;
                long bytesRemaining = entry.size;
                // transferTo isn't guaranteed to transfer all bytes on the
                // first attempt, so keep trying until the entire entry is
                // extracted. If 0 bytes are transferred during 16 consecutive
                // attempts, then throw an IOException.
                int patience = MAX_PATIENCE;
                while (bytesRemaining > 0 && currentPosition < vpChannel.size()) {
                    long bytesTransferred = vpChannel.transferTo(currentPosition, bytesRemaining, destChannel);
                    if (bytesTransferred <= 0L) {
                        if (--patience <= 0) {
                            throw new IOException(String.format("Unable to fully extract %s.", entry));
                        }
                    } else {
                        patience = MAX_PATIENCE;
                    }
                    bytesRemaining -= bytesTransferred;
                }
            } finally {
                if (destChannel != null) {
                    destChannel.close();
                }
            }
        }
    }
    
    /**
     * Renames the given entry to the given name.
     * 
     * @param entry entry to rename
     * @param newName new name of entry, not including path
     * @throws VPEntryNotFoundException if the given entry does not exist
     * @throws VPEntryAlreadyExistsException if an entry with the new name already exists
     * @throws IOException if an I/O error occurs
     */
    public void renameEntry(VPEntry entry, String newName) throws IOException {
        if (!isOpen) {
            throw new IOException("VP file has already been closed.");
        }
        if (readOnly) {
            throw new IOException("VP file is open in read-only mode.");
        }
        if (activeInputStream != null || activeOutputStream != null) {
            throw new IOException("Cannot rename an entry of this VP file while a stream is already open on it.");
        }
        int oldEntryIndex = Collections.binarySearch(entryList, entry);
        if (oldEntryIndex < 0) {
            throw new VPEntryNotFoundException(String.format("%s does not exist in VP file.", entry));
        }
        
        VPEntry realOldEntry = entryList.get(oldEntryIndex);
        VPEntry newEntry = new VPEntry(realOldEntry.offset, realOldEntry.size, realOldEntry.folder, newName, realOldEntry.timestamp);
        if (newEntry.equals(realOldEntry)) {
            return;
        }
        if (Collections.binarySearch(entryList, newEntry) >= 0) {
            throw new VPEntryAlreadyExistsException(String.format("%s already exists in VP file.", newEntry));
        }
        modified = true;
        entryList.set(oldEntryIndex, newEntry);
        Collections.sort(entryList);
    }
    
    /**
     * Renames the entry matching the given name to the given new name.
     * 
     * @param entryName name of entry, including full path, to rename
     * @param newName new name of entry, not including path
     * @throws VPEntryNotFoundException if the given entry does not exist
     * @throws VPEntryAlreadyExistsException if an entry with the new name already exists
     * @throws IOException if an I/O error occurs
     */
    public void renameEntry(String entryName, String newName) throws IOException {
        renameEntry(getEntry(entryName), newName);
    }
    
    /**
     * 
     * 
     * @param entry entry to move
     * @param destination destination folder
     * @throws VPEntryNotFoundException if the given entry does not exist
     * @throws VPEntryAlreadyExistsException if an entry with the new name already exists
     * @throws IOException if an I/O error occurs
     */
    public void moveEntry(VPEntry entry, VPFolder destination) throws IOException {
        if (!isOpen) {
            throw new IOException("VP file has already been closed.");
        }
        if (readOnly) {
            throw new IOException("VP file is open in read-only mode.");
        }
        if (activeInputStream != null || activeOutputStream != null) {
            throw new IOException("Cannot rename an entry of this VP file while a stream is already open on it.");
        }
        int oldEntryIndex = Collections.binarySearch(entryList, entry);
        if (oldEntryIndex < 0) {
            throw new VPEntryNotFoundException(String.format("%s does not exist in VP file.", entry));
        }
        
        VPEntry realOldEntry = entryList.get(oldEntryIndex);
        VPEntry newEntry = new VPEntry(realOldEntry.offset, realOldEntry.size, destination, realOldEntry.name, realOldEntry.timestamp);
        if (newEntry.equals(realOldEntry)) {
            return;
        }
        if (Collections.binarySearch(entryList, newEntry) >= 0) {
            throw new VPEntryAlreadyExistsException(String.format("%s already exists in VP file.", newEntry));
        }
        modified = true;
        if (!folderSet.contains(destination)) {
            folderSet.add(destination);
        }
        entryList.set(oldEntryIndex, newEntry);
        Collections.sort(entryList);
    }
    
    public void moveEntry(VPEntry entry, String destinationName) throws IOException {
        moveEntry(entry, new VPFolder(destinationName));
    }
    
    public void moveEntry(String entryName, VPFolder destination) throws IOException {
        moveEntry(getEntry(entryName), destination);
    }
    
    public void moveEntry(String entryName, String destinationName) throws IOException {
        moveEntry(getEntry(entryName), new VPFolder(destinationName));
    }
    
    public void deleteEmptyFolders() throws IOException {
        if (!isOpen) {
            throw new IOException("VP file has already been closed.");
        }
        if (readOnly) {
            throw new IOException("VP file is open in read-only mode.");
        }
        
        for (Iterator<VPFolder> it = folderSet.iterator(); it.hasNext(); ) {
            VPFolder folder = it.next();
            if (folder.path.length() == 0) {
                continue;
            }
            boolean folderIsEmpty = true;
            for (VPEntry entry : entryList) {
                if (entry.folder.equals(folder)) {
                    folderIsEmpty = false;
                    break;
                }
            }
            if (folderIsEmpty) {
                String folderNameWithBackslash = folder.pathLowercase + "\\";
                for (VPFolder folder2 : folderSet) {
                    if (folder2.pathLowercase.startsWith(folderNameWithBackslash)) {
                        folderIsEmpty = false;
                        break;
                    }
                }
            }
            if (folderIsEmpty) {
                modified = true;
                it.remove();
            }
        }
    }
    
    /**
     * Closes all streams that are currently open on this VP file.
     */
    public void closeStreams() {
        if (activeInputStream != null) {
            activeInputStream.close();
        }
        if (activeOutputStream != null) {
            activeOutputStream.close();
        }
    }
    
    /**
     * Closes this VP file and releases any system resources associated with
     * it. If this VP file has been modified, then the entry list is updated.
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        if (!isOpen) {
            return;
        }
        
        closeStreams();
        if (modified) {
            writeEntryList();
        }
        isOpen = false;
        file.close();
    }
    
    /**
     * Reads a signed 32-bit integer in little-endian format.
     */
    private int readInt() throws IOException {
        return Integer.reverseBytes(file.readInt());
    }
    
    /**
     * Reads an unsigned 32-bit integer in little-endian format.
     */
    private long readUnsignedInt() throws IOException {
        return Integer.reverseBytes(file.readInt()) & 0xffffffffL;
    }
    
    /**
     * Reads a 32-byte string, trimming the first null terminator and all
     * characters after it.
     */
    private String readString() throws IOException {
        byte[] stringBytes = new byte[32];
        file.readFully(stringBytes);
        // In case there's no null terminator at all, set the default length to 31 rather than 32.
        // This way, the string that gets stored won't be different from that one that will be saved to the file,
        // which will definitely have a null terminator.
        int stringLength = stringBytes.length - 1;
        for (int i = 0; i < stringBytes.length; i++) {
            if (stringBytes[i] == 0) {
                stringLength = i;
                break;
            }
        }
        return new String(stringBytes, 0, stringLength, VP_CHARSET_NAME);
    }
    
    /**
     * Writes a signed 32-bit integer in little-endian format.
     */
    private void writeInt(int n) throws IOException {
        file.writeInt(Integer.reverseBytes(n));
    }
    
    /**
     * Writes an unsigned 32-bit integer in little-endian format.
     */
    private void writeUnsignedInt(long n) throws IOException {
        int cappedValue = (int)Math.min(Math.max(0L, n), 0xffffffffL);
        file.writeInt(Integer.reverseBytes(cappedValue));
    }
    
    /**
     * Writes a string to the next 32 bytes using the ISO-8859-1 encoding,
     * padding with nulls or cropping to 32 bytes if necessary.
     */
    private void writeString(String s) throws IOException {
        byte[] stringBytes = s.getBytes(VP_CHARSET_NAME);
        if (stringBytes.length != 32) {
            byte[] copy = new byte[32];
            System.arraycopy(stringBytes, 0, copy, 0, Math.min(stringBytes.length, 32));
            stringBytes = copy;
        }
        // Make sure that the string actually has a null terminator.
        stringBytes[stringBytes.length - 1] = 0;
        file.write(stringBytes);
    }
    
    private void writeEntryList() throws IOException {
        file.seek(entryListOffset);
        
        long entryListSize = 0L;
        VPFolder currentFolder = new VPFolder();
        Set<VPFolder> writtenFolders = new HashSet<VPFolder>(folderSet.size() * 2);
        for (VPFolder nextFolder : folderSet) {
            if (!writtenFolders.contains(nextFolder)) {
                String[] currentFolderArray = currentFolder.path.split("\\\\");
                String[] nextFolderArray = nextFolder.path.split("\\\\");
                if (currentFolderArray.length == 1 && currentFolderArray[0].length() == 0) {
                    currentFolderArray = EMPTY_STRING_ARRAY;
                }
                if (nextFolderArray.length == 1 && nextFolderArray[0].length() == 0) {
                    nextFolderArray = EMPTY_STRING_ARRAY;
                }
                int minLength = Math.min(currentFolderArray.length, nextFolderArray.length);
                int i = 0;
                for ( ; i < minLength; i++) {
                    if (!currentFolderArray[i].equalsIgnoreCase(nextFolderArray[i])) {
                        break;
                    }
                }
                for (int j = i; j < currentFolderArray.length; j++) {
                    writeInt(0);
                    writeInt(0);
                    writeString("..");
                    writeInt(0);
                    entryListSize++;
                }
                for (int j = i; j < nextFolderArray.length; j++) {
                    writeInt(0);
                    writeInt(0);
                    writeString(nextFolderArray[j]);
                    writeInt(0);
                    entryListSize++;
                }
                
                String filter = nextFolder.path.replace("\\", "\\\\");
                if (nextFolderArray.length == 0) {
                    filter += "[^\\\\]*";
                } else {
                    filter += "\\\\[^\\\\]*";
                }
                for (VPEntry entry : getEntryList(filter)) {
                    writeUnsignedInt(entry.offset);
                    writeUnsignedInt(entry.size);
                    writeString(entry.name);
                    writeInt(entry.timestamp);
                    entryListSize++;
                }
                
                writtenFolders.add(nextFolder);
            }
            
            currentFolder = nextFolder;
        }
        
        String[] currentFolderArray = currentFolder.path.split("\\\\");
        if (currentFolderArray.length == 1 && currentFolderArray[0].length() == 0) {
            currentFolderArray = EMPTY_STRING_ARRAY;
        }
        for (int i = 0; i < currentFolderArray.length; i++) {
            for (int j = i; j < currentFolderArray.length; j++) {
                writeInt(0);
                writeInt(0);
                writeString("..");
                writeInt(0);
                entryListSize++;
            }
        }
        
        file.setLength(file.getFilePointer());
        file.seek(8);
        writeUnsignedInt(entryListOffset);
        writeUnsignedInt(entryListSize);
    }
    
    /**
     * Returns the current 32-bit Unix time. If the current time cannot be
     * represented by 32-bit Unix time (that is, if the current date is earlier
     * than December 12, 1901 or later than January 18, 2038), then the
     * returned value is capped, without overflow, at the bounds of the 32-bit
     * Unix time range, provided that the value returned by Java's
     * {@link System#currentTimeMillis()} method is accurate.
     * 
     * @return current 32-bit Unix time
     */
    public static int getCurrentUnixTime() {
        // Convert the current Java time to seconds and cap it at the signed 32-bit integer range.
        long longUnixTime = System.currentTimeMillis() / 1000;
        return (int)Math.min(Math.max(Integer.MIN_VALUE, longUnixTime), Integer.MAX_VALUE);
    }
    
    
    /**
     *
     * @author Ryan Sakowski
     */
    public class VPEntry implements Comparable<VPEntry> {
        
        private long offset;
        private final long size;
        private final VPFolder folder;
        private final String name;
        private final String nameLowercase;
        private final String fullPath;
        private final String fullPathLowercase;
        private final int timestamp;
        
        private VPEntry(long offset, long size, VPFolder folder, String name, int timestamp) {
            this.offset = offset;
            this.size = size;
            this.folder = folder;
            this.name = name;
            this.nameLowercase = this.name.toLowerCase(Locale.ENGLISH);
            if (folder.path.length() == 0) {
                fullPath = name;
            } else {
                fullPath = folder.path + "\\" + name;
            }
            this.fullPathLowercase = this.fullPath.toLowerCase(Locale.ENGLISH);
            this.timestamp = timestamp;
        }
        
        public long getSize() {
            return size;
        }
        
        public String getName() {
            return name;
        }
        
        public String getFullPath() {
            return fullPath;
        }
        
        public VPFolder getFolder() {
            return folder;
        }
        
        public int getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return getFullPath();
        }
        
        @Override
        public int compareTo(VPEntry o) {
            if (this == o) {
                return 0;
            }
            
            if (!folder.equals(o.folder)) {
                return folder.compareTo(o.folder);
            }
            
            int c = nameLowercase.compareTo(o.nameLowercase);
            if (c != 0) {
                return c;
            }
            c = timestamp - o.timestamp;
            if (c != 0) {
                return c;
            }
            return (int)Math.min(Math.max(Integer.MIN_VALUE, size - o.size), Integer.MAX_VALUE);
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof VPEntry)) {
                return false;
            }
            if (this == o) {
                return true;
            }
            
            VPEntry entry2 = (VPEntry)o;
            
            return fullPathLowercase.equals(entry2.fullPathLowercase)
                    && timestamp == entry2.timestamp
                    && size == entry2.size;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + (int) (this.size ^ (this.size >>> 32));
            hash = 41 * hash + (this.fullPathLowercase != null ? this.fullPathLowercase.hashCode() : 0);
            hash = 41 * hash + this.timestamp;
            return hash;
        }
    }
    
    /**
     *
     * @author Ryan Sakowski
     */
    public class VPFolder implements Comparable<VPFolder> {
        
        private final String path;
        private final String pathLowercase;
        
        private VPFolder() {
            path = "";
            pathLowercase = path;
        }
        
        private VPFolder(String pathName) {
            String normalizedPathName = pathName.replaceAll("^[/\\\\]+|[/\\\\]+$", "");
            path = normalizedPathName;
            pathLowercase = path.toLowerCase(Locale.ENGLISH);
        }
        
        private VPFolder(VPFolder parent, String child) {
            String normalizedChild = child.replaceAll("^[/\\\\]+|[/\\\\]+$", "");
            
            if (parent == null || parent.path.length() == 0) {
                path = normalizedChild;
            } else {
                path = parent.path + "\\" + normalizedChild;
            }
            pathLowercase = path.toLowerCase(Locale.ENGLISH);
        }
        
        private VPFolder getParent() {
            if (path.length() == 0) {
                return this;
            } else {
                int lastBackslashPos = path.lastIndexOf('\\');
                if (lastBackslashPos >= 0) {
                    return new VPFolder(path.substring(0, lastBackslashPos));
                } else {
                    return new VPFolder();
                }
            }
        }
        
        @Override
        public String toString() {
            return path;
        }
        
        @Override
        public int compareTo(VPFolder o) {
            if (this == o) {
                return 0;
            }
            
            int path1Length = pathLowercase.length();
            int path2Length = o.pathLowercase.length();
            int minLength = Math.min(path1Length, path2Length);
            
            if (path1Length == 0) {
                if (path2Length == 0) {
                    return 0;
                } else {
                    return 1;
                }
            } else {
                if (path2Length == 0) {
                    return -1;
                }
            }
            
            int i = 0;
            for ( ; i < minLength; i++) {
                char ch1 = pathLowercase.charAt(i);
                char ch2 = o.pathLowercase.charAt(i);
                if (ch1 == '\\' && ch2 != '\\') {
                    return -1;
                } else if (ch1 != '\\' && ch2 == '\\') {
                    return 1;
                } else if (ch1 != ch2) {
                    return ch1 - ch2;
                }
            }
            
            if (i < path1Length && pathLowercase.charAt(i) == '\\') {
                return -1;
            } else if (i < path2Length && o.pathLowercase.charAt(i) == '\\') {
                return 1;
            } else {
                return pathLowercase.length() - o.pathLowercase.length();
            }
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof VPFolder)) {
                return false;
            }
            if (this == o) {
                return true;
            }
            
            return pathLowercase.equals(((VPFolder)o).pathLowercase);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + (this.pathLowercase != null ? this.pathLowercase.hashCode() : 0);
            return hash;
        }
    }
    
    /**
     *
     * @author Ryan Sakowski
     */
    public class VPEntryInputStream extends InputStream {
        
        private long markPosition = -1;
        private final long startLimit;
        private final long endLimit;
        private boolean isOpen = true;
        
        private VPEntryInputStream(VPEntry entry) throws IOException {
            startLimit = entry.offset;
            endLimit = entry.offset + entry.size;
            file.seek(entry.offset);
        }
        
        @Override
        public int read() throws IOException {
            if (!isOpen) {
                throw new IOException("Stream has already been closed.");
            }
            
            if (file.getFilePointer() < endLimit) {
                return file.read();
            } else {
                return -1;
            }
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (!isOpen) {
                throw new IOException("Stream has already been closed.");
            }
            
            if (b == null) {
                throw new NullPointerException();
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }
            
            long bytesRemaining = endLimit - file.getFilePointer();
            if (bytesRemaining <= 0L) {
                return -1;
            } else if (len <= bytesRemaining) {
                return file.read(b, off, len);
            } else {
                return file.read(b, off, (int)bytesRemaining);
            }
        }
        
        @Override
        public boolean markSupported() {
            return true;
        }
        
        @Override
        public void mark(int readlimit) {
            if (!isOpen) {
                return;
            }
            
            try {
                markPosition = file.getFilePointer();
            } catch (IOException ex) {
                // IOException cannot be thrown from this method, so suppress it here.
                markPosition = -1;
            }
        }
        
        @Override
        public void reset() throws IOException {
            if (markPosition < 0) {
                throw new IOException("Stream has not been marked or mark has been invalidated.");
            }
            if (!isOpen) {
                return;
            }
            
            file.seek(markPosition);
        }
        
        @Override
        public long skip(long n) throws IOException {
            if (!isOpen) {
                return 0L;
            }
            
            long currentPosition = file.getFilePointer();
            if (n > 0L && currentPosition < endLimit) {
                long bytesToAdvance = Math.min(n, endLimit - currentPosition);
                file.seek(currentPosition + bytesToAdvance);
                return bytesToAdvance;
            } else if (n < 0L && currentPosition > startLimit) {
                long bytesToAdvance = Math.max(n, startLimit - currentPosition);
                file.seek(currentPosition + bytesToAdvance);
                return bytesToAdvance;
            } else {
                return 0L;
            }
        }
        
        @Override
        public void close() {
            if (!isOpen) {
                return;
            }
            
            isOpen = false;
            activeInputStream = null;
        }
    }
    
    /**
     *
     * @author Ryan Sakowski
     */
    public class VPEntryOutputStream extends OutputStream {
        
        private final long endLimit;
        private long bytesWritten;
        private boolean isOpen;
        
        private VPEntryOutputStream(long endLimit) {
            this.endLimit = endLimit;
        }
        
        @Override
        public void write(int b) throws IOException {
            if (!isOpen) {
                throw new IOException("Stream has already been closed.");
            }
            
            if (file.getFilePointer() < endLimit) {
                file.write(b);
                bytesWritten++;
            } else {
                throw new IOException("Size limit of VP file has been reached.");
            }
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (!isOpen) {
                throw new IOException("Stream has already been closed.");
            }
            
            if (b == null) {
                throw new NullPointerException();
            } else if ((off < 0) || (off > b.length) || (len < 0) ||
                       ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return;
            }
            
            if (file.getFilePointer() + len < endLimit) {
                file.write(b, off, len);
                bytesWritten += len;
            } else {
                throw new IOException("Size limit of VP file has been reached.");
            }
        }
        
        @Override
        public void close() {
            if (!isOpen) {
                return;
            }
            
            isOpen = false;
            if (bytesWritten > 0) {
                
            }
            activeOutputStream = null;
        }
    }
}
