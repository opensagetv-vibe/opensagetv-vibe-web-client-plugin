package org.opensagetv.webplayer;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Reflection-only bridge to the sagex API supplied by the SageTV Jetty plugin.
 * Keeping sagex out of WEB-INF/lib avoids bundling a second copy of SageTV APIs.
 */
public final class SageApiBridge {
    private final Method getMediaFileForId;
    private final Method getMediaFiles;
    private final Method getMediaFileId;
    private final Method getMediaFileForFilePath;
    private final Method isTVFile;
    private final Method getMediaFileAiring;
    private final Method getFileForSegment;
    private final Method getNumberOfSegments;
    private final Method getMediaTitle;
    private final Method isFileCurrentlyRecording;
    private final Method getServerAddress;
    private final Method getMediaFileEncoding;
    private final Method getMediaFileFormatDescription;
    private final Method getFileStartTime;
    private final Method getFileDuration;
    private final Method deleteFile;
    private final Method deleteFileWithoutPrejudice;
    private final Method isDVD;
    private final Method isBluRay;
    private final Method isDVDDrive;
    private final Method getMediaFileMetadata;

    private final Method getAiringStartTime;
    private final Method getAiringDuration;
    private final Method getAiringChannelName;
    private final Method getAiringChannelNumber;
    private final Method isWatched;
    private final Method getShow;
    private final Method getChannel;
    private final Method getAiringForId;
    private final Method getAiringId;
    private final Method getAiringTitle;
    private final Method getAiringEndTime;
    private final Method getScheduleStartTime;
    private final Method getScheduleEndTime;
    private final Method getRecordingQuality;
    private final Method setRecordingQuality;
    private final Method setRecordingTimes;
    private final Method getMediaFileForAiring;
    private final Method isManualRecord;
    private final Method recordAiring;
    private final Method cancelRecord;
    private final Method setWatched;
    private final Method clearWatched;
    private final Method setWatchedTimes;
    private final Method getLatestWatchedTime;
    private final Method isWatchedCompletely;

    private final Method getAiringsOnViewableChannelsAtTime;
    private final Method sortDatabase;
    private final Method getScheduledRecordings;
    private final Method getScheduledRecordingsForDevice;
    private final Method getAiringsThatWontBeRecorded;
    private final Method getRecentlyWatched;
    private final Method getCurrentlyRecordingMediaFiles;

    private final Method getShowEpisode;
    private final Method getShowDescription;
    private final Method getShowCategory;
    private final Method getShowYear;

    private final Method getAllChannels;
    private final Method getChannelName;
    private final Method getChannelNetwork;
    private final Method getChannelNumber;
    private final Method getStationId;
    private final Method isChannelViewable;
    private final Method getChannelLogoUrl;

    private final Method getFavorites;
    private final Method getFavoriteForId;
    private final Method getFavoriteId;
    private final Method getFavoriteTitle;
    private final Method getFavoriteDescription;
    private final Method getFavoriteCategory;
    private final Method getFavoriteSubCategory;
    private final Method getFavoriteChannel;
    private final Method getFavoriteNetwork;
    private final Method getFavoriteKeyword;
    private final Method getFavoriteTimeslot;
    private final Method getStartPadding;
    private final Method getStopPadding;
    private final Method getFavoriteQuality;
    private final Method getKeepAtMost;
    private final Method isAutoDelete;
    private final Method isFavoriteEnabled;
    private final Method isFirstRuns;
    private final Method isReRuns;
    private final Method addFavorite;
    private final Method removeFavorite;
    private final Method setFavoriteEnabled;
    private final Method setStartPadding;
    private final Method setStopPadding;
    private final Method setFavoriteQuality;
    private final Method setKeepAtMost;
    private final Method setDontAutodelete;
    private final Method setRunStatus;
    private final Method createFavoritePriority;

    private final Method getCaptureDevices;
    private final Method getCaptureDeviceInputs;
    private final Method isCaptureDeviceFunctioning;
    private final Method isCaptureDeviceNetworkEncoder;
    private final Method getActiveCaptureDevices;
    private final Method isCaptureDeviceInUseByLiveClient;
    private final Method getCaptureDeviceCurrentRecordFile;
    private final Method getCaptureDeviceQualities;
    private final Method getCaptureDeviceDefaultQuality;
    private final Method setCaptureDeviceDefaultQuality;
    private final Method getCaptureDeviceBroadcastStandard;
    private final Method getCaptureDeviceMerit;
    private final Method setCaptureDeviceMerit;

    private final Method getLineupForCaptureDeviceInput;
    private final Method getSignalStrength;
    private final Method getCaptureDeviceInputBroadcastStandard;

    private final Method getDefaultRecordingQuality;
    private final Method setDefaultRecordingQuality;
    private final Method getRecordingQualities;
    private final Method getRecordingQualityBitrate;
    private final Method getRecordingQualityFormat;

    private SageApiBridge() throws Exception {
        Class<?> mediaFileApi = Class.forName("sagex.api.MediaFileAPI");
        getMediaFileForId = mediaFileApi.getMethod("GetMediaFileForID", int.class);
        getMediaFiles = mediaFileApi.getMethod("GetMediaFiles");
        getMediaFileId = mediaFileApi.getMethod("GetMediaFileID", Object.class);
        getMediaFileForFilePath = mediaFileApi.getMethod("GetMediaFileForFilePath", File.class);
        isTVFile = mediaFileApi.getMethod("IsTVFile", Object.class);
        getMediaFileAiring = mediaFileApi.getMethod("GetMediaFileAiring", Object.class);
        getFileForSegment = mediaFileApi.getMethod("GetFileForSegment", Object.class, int.class);
        getNumberOfSegments = mediaFileApi.getMethod("GetNumberOfSegments", Object.class);
        getMediaTitle = mediaFileApi.getMethod("GetMediaTitle", Object.class);
        isFileCurrentlyRecording = mediaFileApi.getMethod("IsFileCurrentlyRecording", Object.class);
        getMediaFileEncoding = mediaFileApi.getMethod("GetMediaFileEncoding", Object.class);
        getMediaFileFormatDescription = mediaFileApi.getMethod("GetMediaFileFormatDescription", Object.class);
        getFileStartTime = mediaFileApi.getMethod("GetFileStartTime", Object.class);
        getFileDuration = optionalMethod(mediaFileApi, "GetFileDuration", Object.class);
        deleteFile = mediaFileApi.getMethod("DeleteFile", Object.class);
        deleteFileWithoutPrejudice = mediaFileApi.getMethod("DeleteFileWithoutPrejudice", Object.class);
        isDVD = optionalMethod(mediaFileApi, "IsDVD", Object.class);
        isBluRay = optionalMethod(mediaFileApi, "IsBluRay", Object.class);
        isDVDDrive = optionalMethod(mediaFileApi, "IsDVDDrive", Object.class);
        getMediaFileMetadata = optionalMethod(mediaFileApi, "GetMediaFileMetadata", Object.class, String.class);

        Class<?> airingApi = Class.forName("sagex.api.AiringAPI");
        getAiringStartTime = airingApi.getMethod("GetAiringStartTime", Object.class);
        getAiringDuration = airingApi.getMethod("GetAiringDuration", Object.class);
        getAiringChannelName = airingApi.getMethod("GetAiringChannelName", Object.class);
        getAiringChannelNumber = airingApi.getMethod("GetAiringChannelNumber", Object.class);
        isWatched = airingApi.getMethod("IsWatched", Object.class);
        getShow = airingApi.getMethod("GetShow", Object.class);
        getChannel = airingApi.getMethod("GetChannel", Object.class);
        getAiringForId = airingApi.getMethod("GetAiringForID", int.class);
        getAiringId = airingApi.getMethod("GetAiringID", Object.class);
        getAiringTitle = airingApi.getMethod("GetAiringTitle", Object.class);
        getAiringEndTime = airingApi.getMethod("GetAiringEndTime", Object.class);
        getScheduleStartTime = airingApi.getMethod("GetScheduleStartTime", Object.class);
        getScheduleEndTime = airingApi.getMethod("GetScheduleEndTime", Object.class);
        getRecordingQuality = airingApi.getMethod("GetRecordingQuality", Object.class);
        setRecordingQuality = airingApi.getMethod("SetRecordingQuality", Object.class, String.class);
        setRecordingTimes = airingApi.getMethod("SetRecordingTimes", Object.class, long.class, long.class);
        getMediaFileForAiring = airingApi.getMethod("GetMediaFileForAiring", Object.class);
        isManualRecord = airingApi.getMethod("IsManualRecord", Object.class);
        recordAiring = airingApi.getMethod("Record", Object.class);
        cancelRecord = airingApi.getMethod("CancelRecord", Object.class);
        setWatched = airingApi.getMethod("SetWatched", Object.class);
        clearWatched = airingApi.getMethod("ClearWatched", Object.class);
        setWatchedTimes = airingApi.getMethod("SetWatchedTimes", Object.class, long.class, long.class);
        getLatestWatchedTime = airingApi.getMethod("GetLatestWatchedTime", Object.class);
        isWatchedCompletely = airingApi.getMethod("IsWatchedCompletely", Object.class);

        Class<?> databaseApi = Class.forName("sagex.api.Database");
        getAiringsOnViewableChannelsAtTime = databaseApi.getMethod("GetAiringsOnViewableChannelsAtTime", long.class, long.class, boolean.class);
        sortDatabase = databaseApi.getMethod("Sort", Object.class, boolean.class, Object.class);

        Class<?> showApi = Class.forName("sagex.api.ShowAPI");
        getShowEpisode = showApi.getMethod("GetShowEpisode", Object.class);
        getShowDescription = showApi.getMethod("GetShowDescription", Object.class);
        getShowCategory = showApi.getMethod("GetShowCategory", Object.class);
        getShowYear = showApi.getMethod("GetShowYear", Object.class);

        Class<?> globalApi = Class.forName("sagex.api.Global");
        getServerAddress = globalApi.getMethod("GetServerAddress");
        getScheduledRecordings = globalApi.getMethod("GetScheduledRecordings");
        getScheduledRecordingsForDevice = globalApi.getMethod("GetScheduledRecordingsForDevice", String.class);
        getAiringsThatWontBeRecorded = globalApi.getMethod("GetAiringsThatWontBeRecorded", boolean.class);
        getRecentlyWatched = globalApi.getMethod("GetRecentlyWatched", long.class);
        getCurrentlyRecordingMediaFiles = globalApi.getMethod("GetCurrentlyRecordingMediaFiles");

        Class<?> channelApi = Class.forName("sagex.api.ChannelAPI");
        getAllChannels = channelApi.getMethod("GetAllChannels");
        getChannelName = channelApi.getMethod("GetChannelName", Object.class);
        getChannelNetwork = channelApi.getMethod("GetChannelNetwork", Object.class);
        getChannelNumber = channelApi.getMethod("GetChannelNumber", Object.class);
        getStationId = channelApi.getMethod("GetStationID", Object.class);
        isChannelViewable = channelApi.getMethod("IsChannelViewable", Object.class);
        getChannelLogoUrl = channelApi.getMethod("GetChannelLogoURL", Object.class);

        Class<?> favoriteApi = Class.forName("sagex.api.FavoriteAPI");
        getFavorites = favoriteApi.getMethod("GetFavorites");
        getFavoriteForId = favoriteApi.getMethod("GetFavoriteForID", int.class);
        getFavoriteId = favoriteApi.getMethod("GetFavoriteID", Object.class);
        getFavoriteTitle = favoriteApi.getMethod("GetFavoriteTitle", Object.class);
        getFavoriteDescription = favoriteApi.getMethod("GetFavoriteDescription", Object.class);
        getFavoriteCategory = favoriteApi.getMethod("GetFavoriteCategory", Object.class);
        getFavoriteSubCategory = favoriteApi.getMethod("GetFavoriteSubCategory", Object.class);
        getFavoriteChannel = favoriteApi.getMethod("GetFavoriteChannel", Object.class);
        getFavoriteNetwork = favoriteApi.getMethod("GetFavoriteNetwork", Object.class);
        getFavoriteKeyword = favoriteApi.getMethod("GetFavoriteKeyword", Object.class);
        getFavoriteTimeslot = favoriteApi.getMethod("GetFavoriteTimeslot", Object.class);
        getStartPadding = favoriteApi.getMethod("GetStartPadding", Object.class);
        getStopPadding = favoriteApi.getMethod("GetStopPadding", Object.class);
        getFavoriteQuality = favoriteApi.getMethod("GetFavoriteQuality", Object.class);
        getKeepAtMost = favoriteApi.getMethod("GetKeepAtMost", Object.class);
        isAutoDelete = favoriteApi.getMethod("IsAutoDelete", Object.class);
        isFavoriteEnabled = favoriteApi.getMethod("IsFavoriteEnabled", Object.class);
        isFirstRuns = favoriteApi.getMethod("IsFirstRuns", Object.class);
        isReRuns = favoriteApi.getMethod("IsReRuns", Object.class);
        addFavorite = favoriteApi.getMethod("AddFavorite", String.class, boolean.class, boolean.class, String.class, String.class, Object.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class);
        removeFavorite = favoriteApi.getMethod("RemoveFavorite", Object.class);
        setFavoriteEnabled = favoriteApi.getMethod("SetFavoriteEnabled", Object.class, boolean.class);
        setStartPadding = favoriteApi.getMethod("SetStartPadding", Object.class, long.class);
        setStopPadding = favoriteApi.getMethod("SetStopPadding", Object.class, long.class);
        setFavoriteQuality = favoriteApi.getMethod("SetFavoriteQuality", Object.class, String.class);
        setKeepAtMost = favoriteApi.getMethod("SetKeepAtMost", Object.class, int.class);
        setDontAutodelete = favoriteApi.getMethod("SetDontAutodelete", Object.class, boolean.class);
        setRunStatus = favoriteApi.getMethod("SetRunStatus", Object.class, boolean.class, boolean.class);
        createFavoritePriority = favoriteApi.getMethod("CreateFavoritePriority", Object.class, Object.class);

        Class<?> captureApi = Class.forName("sagex.api.CaptureDeviceAPI");
        getCaptureDevices = captureApi.getMethod("GetCaptureDevices");
        getCaptureDeviceInputs = captureApi.getMethod("GetCaptureDeviceInputs", String.class);
        isCaptureDeviceFunctioning = captureApi.getMethod("IsCaptureDeviceFunctioning", String.class);
        isCaptureDeviceNetworkEncoder = captureApi.getMethod("IsCaptureDeviceANetworkEncoder", String.class);
        getActiveCaptureDevices = captureApi.getMethod("GetActiveCaptureDevices");
        isCaptureDeviceInUseByLiveClient = captureApi.getMethod("IsCaptureDeviceInUseByALiveClient", String.class);
        getCaptureDeviceCurrentRecordFile = captureApi.getMethod("GetCaptureDeviceCurrentRecordFile", String.class);
        getCaptureDeviceQualities = captureApi.getMethod("GetCaptureDeviceQualities", String.class);
        getCaptureDeviceDefaultQuality = captureApi.getMethod("GetCaptureDeviceDefaultQuality", String.class);
        setCaptureDeviceDefaultQuality = captureApi.getMethod("SetCaptureDeviceDefaultQuality", String.class, String.class);
        getCaptureDeviceBroadcastStandard = captureApi.getMethod("GetCaptureDeviceBroadcastStandard", String.class);
        getCaptureDeviceMerit = captureApi.getMethod("GetCaptureDeviceMerit", String.class);
        setCaptureDeviceMerit = captureApi.getMethod("SetCaptureDeviceMerit", String.class, int.class);

        Class<?> inputApi = Class.forName("sagex.api.CaptureDeviceInputAPI");
        getLineupForCaptureDeviceInput = inputApi.getMethod("GetLineupForCaptureDeviceInput", String.class);
        getSignalStrength = inputApi.getMethod("GetSignalStrength", String.class);
        getCaptureDeviceInputBroadcastStandard = inputApi.getMethod("GetCaptureDeviceInputBroadcastStandard", String.class);

        Class<?> configApi = Class.forName("sagex.api.Configuration");
        getDefaultRecordingQuality = configApi.getMethod("GetDefaultRecordingQuality");
        setDefaultRecordingQuality = configApi.getMethod("SetDefaultRecordingQuality", String.class);
        getRecordingQualities = configApi.getMethod("GetRecordingQualities");
        getRecordingQualityBitrate = configApi.getMethod("GetRecordingQualityBitrate", String.class);
        getRecordingQualityFormat = configApi.getMethod("GetRecordingQualityFormat", String.class);
    }

    public static SageApiBridge create() {
        try { return new SageApiBridge(); }
        catch (Exception e) { throw new IllegalStateException("sagex-api is not visible or is too old. Verify SageTV Jetty and sagex-api 9.1.7+ are installed.", e); }
    }

    private static Method optionalMethod(Class<?> type, String name, Class<?>... args) {
        try { return type.getMethod(name, args); } catch (NoSuchMethodException e) { return null; }
    }

    public Object getMediaFile(int id) { return invoke(getMediaFileForId, id); }
    public Object[] getMediaFiles() { return asArray(invoke(getMediaFiles)); }
    public int getMediaFileId(Object mediaFile) { return integer(invoke(getMediaFileId, mediaFile)); }
    public Object getMediaFileForFilePath(File file) { return invoke(getMediaFileForFilePath, file); }
    public boolean isTVFile(Object mediaFile) { return bool(invoke(isTVFile, mediaFile)); }
    public Object getMediaFileAiring(Object mediaFile) { return invoke(getMediaFileAiring, mediaFile); }
    public File getFileForSegment(Object mediaFile, int segment) { return (File) invoke(getFileForSegment, mediaFile, segment); }
    public int getNumberOfSegments(Object mediaFile) { return integer(invoke(getNumberOfSegments, mediaFile)); }
    public String getMediaTitle(Object mediaFile) { return string(invoke(getMediaTitle, mediaFile)); }
    public boolean isFileCurrentlyRecording(Object mediaFile) { return bool(invoke(isFileCurrentlyRecording, mediaFile)); }
    public String getServerAddress() { return string(invoke(getServerAddress)); }
    public String getMediaFileEncoding(Object mediaFile) { return string(invoke(getMediaFileEncoding, mediaFile)); }
    public String getMediaFileFormatDescription(Object mediaFile) { return string(invoke(getMediaFileFormatDescription, mediaFile)); }
    public long getPlaybackDuration(Object mediaFile) {
        Object airing = getMediaFileAiring(mediaFile);
        long scheduled = airing == null ? 0L : getAiringDuration(airing);
        long recorded = getFileDuration == null ? 0L : longValue(invoke(getFileDuration, mediaFile));
        if (isFileCurrentlyRecording(mediaFile)) return Math.max(recorded, scheduled);
        return recorded > 0L ? recorded : scheduled;
    }
    public long getFileStartTime(Object mediaFile) { return longValue(invoke(getFileStartTime, mediaFile)); }
    public boolean deleteFile(Object mediaFile) { return bool(invoke(deleteFile, mediaFile)); }
    public boolean deleteFileWithoutPrejudice(Object mediaFile) { return bool(invoke(deleteFileWithoutPrejudice, mediaFile)); }
    public boolean isDVD(Object mediaFile) { return isDVD != null && bool(invoke(isDVD, mediaFile)); }
    public boolean isBluRay(Object mediaFile) { return isBluRay != null && bool(invoke(isBluRay, mediaFile)); }
    public boolean isDVDDrive(Object mediaFile) { return isDVDDrive != null && bool(invoke(isDVDDrive, mediaFile)); }
    public long getFileDurationRaw(Object mediaFile) { return getFileDuration == null ? 0L : longValue(invoke(getFileDuration, mediaFile)); }
    public String getMediaFileMetadata(Object mediaFile,String name) { return getMediaFileMetadata == null ? "" : string(invoke(getMediaFileMetadata, mediaFile, name)); }

    public long getAiringStartTime(Object airing) { return longValue(invoke(getAiringStartTime, airing)); }
    public long getAiringDuration(Object airing) { return longValue(invoke(getAiringDuration, airing)); }
    public String getAiringChannelName(Object airing) { return string(invoke(getAiringChannelName, airing)); }
    public String getAiringChannelNumber(Object airing) { return string(invoke(getAiringChannelNumber, airing)); }
    public boolean isWatched(Object airing) { return bool(invoke(isWatched, airing)); }
    public Object getShow(Object airing) { return invoke(getShow, airing); }
    public Object getChannel(Object airing) { return invoke(getChannel, airing); }
    public Object getAiringForId(int id) { return invoke(getAiringForId, id); }
    public int getAiringId(Object airing) { return integer(invoke(getAiringId, airing)); }
    public String getAiringTitle(Object airing) { return string(invoke(getAiringTitle, airing)); }
    public long getAiringEndTime(Object airing) { return longValue(invoke(getAiringEndTime, airing)); }
    public long getScheduleStartTime(Object airing) { return longValue(invoke(getScheduleStartTime, airing)); }
    public long getScheduleEndTime(Object airing) { return longValue(invoke(getScheduleEndTime, airing)); }
    public String getRecordingQuality(Object airing) { return string(invoke(getRecordingQuality, airing)); }
    public void setRecordingQuality(Object airing, String quality) { invoke(setRecordingQuality, airing, quality == null ? "" : quality); }
    public Object setRecordingTimes(Object airing, long start, long stop) { return invoke(setRecordingTimes, airing, start, stop); }
    public Object getMediaFileForAiring(Object airing) { return invoke(getMediaFileForAiring, airing); }
    public boolean isManualRecord(Object airing) { return bool(invoke(isManualRecord, airing)); }
    public Object recordAiring(Object airing) { return invoke(recordAiring, airing); }
    public void cancelRecord(Object airing) { invoke(cancelRecord, airing); }
    public void setWatched(Object airing) { invoke(setWatched, airing); }
    public void clearWatched(Object airing) { invoke(clearWatched, airing); }
    public void setWatchedTimes(Object airing, long watchedEndTime, long realStartTime) { invoke(setWatchedTimes, airing, watchedEndTime, realStartTime); }
    public long getLatestWatchedTime(Object airing) { return longValue(invoke(getLatestWatchedTime, airing)); }
    public boolean isWatchedCompletely(Object airing) { return bool(invoke(isWatchedCompletely, airing)); }

    public Object[] getAiringsOnViewableChannelsAtTime(long start, long end, boolean mustStart) { return asArray(invoke(getAiringsOnViewableChannelsAtTime, start, end, mustStart)); }
    public Object[] sort(Object data, boolean descending, String technique) { return asArray(invoke(sortDatabase, data, descending, technique)); }
    public Object[] getScheduledRecordings() { return asArray(invoke(getScheduledRecordings)); }
    public Object[] getScheduledRecordingsForDevice(String device) { return asArray(invoke(getScheduledRecordingsForDevice, device)); }
    public Object[] getAiringsThatWontBeRecorded(boolean onlyUnresolved) { return asArray(invoke(getAiringsThatWontBeRecorded, onlyUnresolved)); }
    public Object[] getRecentlyWatched(long lookbackMs) { return asArray(invoke(getRecentlyWatched, lookbackMs)); }
    public Object[] getCurrentlyRecordingMediaFiles() { return asArray(invoke(getCurrentlyRecordingMediaFiles)); }

    public String getShowEpisode(Object show) { return string(invoke(getShowEpisode, show)); }
    public String getShowDescription(Object show) { return string(invoke(getShowDescription, show)); }
    public String getShowCategory(Object show) { return string(invoke(getShowCategory, show)); }
    public String getShowYear(Object show) { return string(invoke(getShowYear, show)); }

    public Object[] getAllChannels() { return asArray(invoke(getAllChannels)); }
    public String getChannelName(Object channel) { return string(invoke(getChannelName, channel)); }
    public String getChannelNetwork(Object channel) { return string(invoke(getChannelNetwork, channel)); }
    public String getChannelNumber(Object channel) { return string(invoke(getChannelNumber, channel)); }
    public int getStationId(Object channel) { return integer(invoke(getStationId, channel)); }
    public boolean isChannelViewable(Object channel) { return bool(invoke(isChannelViewable, channel)); }
    public String getChannelLogoUrl(Object channel) { return string(invoke(getChannelLogoUrl, channel)); }

    public Object[] getFavorites() { return asArray(invoke(getFavorites)); }
    public Object getFavoriteForId(int id) { return invoke(getFavoriteForId, id); }
    public int getFavoriteId(Object favorite) { return integer(invoke(getFavoriteId, favorite)); }
    public String getFavoriteTitle(Object favorite) { return string(invoke(getFavoriteTitle, favorite)); }
    public String getFavoriteDescription(Object favorite) { return string(invoke(getFavoriteDescription, favorite)); }
    public String getFavoriteCategory(Object favorite) { return string(invoke(getFavoriteCategory, favorite)); }
    public String getFavoriteSubCategory(Object favorite) { return string(invoke(getFavoriteSubCategory, favorite)); }
    public String getFavoriteChannel(Object favorite) { return string(invoke(getFavoriteChannel, favorite)); }
    public String getFavoriteNetwork(Object favorite) { return string(invoke(getFavoriteNetwork, favorite)); }
    public String getFavoriteKeyword(Object favorite) { return string(invoke(getFavoriteKeyword, favorite)); }
    public String getFavoriteTimeslot(Object favorite) { return string(invoke(getFavoriteTimeslot, favorite)); }
    public long getStartPadding(Object favorite) { return longValue(invoke(getStartPadding, favorite)); }
    public long getStopPadding(Object favorite) { return longValue(invoke(getStopPadding, favorite)); }
    public String getFavoriteQuality(Object favorite) { return string(invoke(getFavoriteQuality, favorite)); }
    public int getKeepAtMost(Object favorite) { return integer(invoke(getKeepAtMost, favorite)); }
    public boolean isAutoDelete(Object favorite) { return bool(invoke(isAutoDelete, favorite)); }
    public boolean isFavoriteEnabled(Object favorite) { return bool(invoke(isFavoriteEnabled, favorite)); }
    public boolean isFirstRuns(Object favorite) { return bool(invoke(isFirstRuns, favorite)); }
    public boolean isReRuns(Object favorite) { return bool(invoke(isReRuns, favorite)); }
    public Object addFavorite(String title, boolean firstRuns, boolean reRuns, String category, String subCategory, String network, String channelCallSign, String timeslot, String keyword) {
        return invoke(addFavorite, nz(title), firstRuns, reRuns, nz(category), nz(subCategory), null, "", "", "", "", nz(network), nz(channelCallSign), nz(timeslot), nz(keyword));
    }
    public void removeFavorite(Object favorite) { invoke(removeFavorite, favorite); }
    public void setFavoriteEnabled(Object favorite, boolean enabled) { invoke(setFavoriteEnabled, favorite, enabled); }
    public void setStartPadding(Object favorite, long ms) { invoke(setStartPadding, favorite, ms); }
    public void setStopPadding(Object favorite, long ms) { invoke(setStopPadding, favorite, ms); }
    public void setFavoriteQuality(Object favorite, String quality) { invoke(setFavoriteQuality, favorite, nz(quality)); }
    public void setKeepAtMost(Object favorite, int count) { invoke(setKeepAtMost, favorite, count); }
    public void setFavoriteAutoDelete(Object favorite, boolean autoDelete) { invoke(setDontAutodelete, favorite, !autoDelete); }
    public boolean setRunStatus(Object favorite, boolean firstRuns, boolean reRuns) { return bool(invoke(setRunStatus, favorite, firstRuns, reRuns)); }
    public void createFavoritePriority(Object higher, Object lower) { invoke(createFavoritePriority, higher, lower); }

    public String[] getCaptureDevices() { return stringArray(invoke(getCaptureDevices)); }
    public String[] getCaptureDeviceInputs(String device) { return stringArray(invoke(getCaptureDeviceInputs, device)); }
    public boolean isCaptureDeviceFunctioning(String device) { return bool(invoke(isCaptureDeviceFunctioning, device)); }
    public boolean isCaptureDeviceNetworkEncoder(String device) { return bool(invoke(isCaptureDeviceNetworkEncoder, device)); }
    public String[] getActiveCaptureDevices() { return stringArray(invoke(getActiveCaptureDevices)); }
    public boolean isCaptureDeviceInUseByLiveClient(String device) { return bool(invoke(isCaptureDeviceInUseByLiveClient, device)); }
    public Object getCaptureDeviceCurrentRecordFile(String device) { return invoke(getCaptureDeviceCurrentRecordFile, device); }
    public String[] getCaptureDeviceQualities(String device) { return stringArray(invoke(getCaptureDeviceQualities, device)); }
    public String getCaptureDeviceDefaultQuality(String device) { return string(invoke(getCaptureDeviceDefaultQuality, device)); }
    public void setCaptureDeviceDefaultQuality(String device, String quality) { invoke(setCaptureDeviceDefaultQuality, device, quality); }
    public String getCaptureDeviceBroadcastStandard(String device) { return string(invoke(getCaptureDeviceBroadcastStandard, device)); }
    public int getCaptureDeviceMerit(String device) { return integer(invoke(getCaptureDeviceMerit, device)); }
    public void setCaptureDeviceMerit(String device, int merit) { invoke(setCaptureDeviceMerit, device, merit); }
    public String getLineupForCaptureDeviceInput(String input) { return string(invoke(getLineupForCaptureDeviceInput, input)); }
    public int getSignalStrength(String input) { return integer(invoke(getSignalStrength, input)); }
    public String getCaptureDeviceInputBroadcastStandard(String input) { return string(invoke(getCaptureDeviceInputBroadcastStandard, input)); }

    public String getDefaultRecordingQuality() { return string(invoke(getDefaultRecordingQuality)); }
    public void setDefaultRecordingQuality(String quality) { invoke(setDefaultRecordingQuality, quality); }
    public String[] getRecordingQualities() { return stringArray(invoke(getRecordingQualities)); }
    public long getRecordingQualityBitrate(String quality) { return longValue(invoke(getRecordingQualityBitrate, quality)); }
    public String getRecordingQualityFormat(String quality) { return string(invoke(getRecordingQualityFormat, quality)); }

    private static Object[] asArray(Object value) {
        if (value instanceof Object[]) return (Object[]) value;
        if (value instanceof Collection) return ((Collection<?>) value).toArray();
        return new Object[0];
    }
    private static String[] stringArray(Object value) {
        if (value instanceof String[]) return (String[]) value;
        Object[] raw = asArray(value); String[] out = new String[raw.length];
        for (int i=0;i<raw.length;i++) out[i]=string(raw[i]);
        return out;
    }
    private static String string(Object value) { return value == null ? "" : value.toString(); }
    private static String nz(String value) { return value == null ? "" : value; }
    private static boolean bool(Object value) { return value instanceof Boolean && (Boolean) value; }
    private static int integer(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    private static long longValue(Object value) { return value == null ? 0L : ((Number) value).longValue(); }

    private Object invoke(Method method, Object... args) {
        try { return method.invoke(null, args); }
        catch (Exception e) { throw new IllegalStateException("SageTV API call failed: " + method.getName(), e); }
    }
}
