/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;

public class DicomSpecialElement extends MediaElement {

    protected final String label;
    protected final String shortLabel;

    public DicomSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO, null);
        DicomObject dicom = mediaIO.getDicomObject();
        String clabel = dicom.getString(Tag.ContentLabel);
        if (clabel == null) {
            clabel = dicom.getString(Tag.ContentDescription);
            if (clabel == null) {
                clabel = (String) getTagValue(TagW.SeriesDescription);
                if (clabel == null) {
                    clabel = getTagValue(TagW.Modality) + " " + getTagValue(TagW.InstanceNumber); //$NON-NLS-1$
                }
            }
        }

        label = clabel;
        if (label.length() > 50) {
            shortLabel = label.substring(0, 47) + "..."; //$NON-NLS-1$
        } else {
            shortLabel = label;
        }

    }

    @Override
    public DicomMediaIO getMediaReader() {
        return (DicomMediaIO) super.getMediaReader();
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return shortLabel;
    }

    @Override
    public void dispose() {
    }

    public static final List<DicomSpecialElement> getPRfromSopUID(String seriesUID, String sopUID, Integer frameNumber,
        List<DicomSpecialElement> studyElements) {
        List<DicomSpecialElement> filteredList = new ArrayList<DicomSpecialElement>();
        if (studyElements != null && seriesUID != null && sopUID != null) {
            for (DicomSpecialElement dicom : studyElements) {
                if (dicom != null && "PR".equals(dicom.getTagValue(TagW.Modality))) { //$NON-NLS-1$
                    if (isSopuidInReferencedSeriesSequence(
                        (DicomElement) dicom.getTagValue(TagW.ReferencedSeriesSequence), seriesUID, sopUID, frameNumber)) {
                        filteredList.add(dicom);
                    }
                }
            }
        }
        return filteredList;
    }

    public static List<DicomSpecialElement> getKOfromSopUID(String seriesUID, List<DicomSpecialElement> studyElements) {
        List<DicomSpecialElement> filteredList = new ArrayList<DicomSpecialElement>();
        if (studyElements != null) {
            for (DicomSpecialElement dicom : studyElements) {
                if (dicom != null && seriesUID != null && "KO".equals(dicom.getTagValue(TagW.Modality))) { //$NON-NLS-1$
                    DicomElement seq = (DicomElement) dicom.getTagValue(TagW.CurrentRequestedProcedureEvidenceSequence);
                    if (seq != null && seq.vr() == VR.SQ) {
                        for (int i = 0; i < seq.countItems(); ++i) {
                            DicomObject dcmObj = null;
                            try {
                                dcmObj = seq.getDicomObject(i);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (dcmObj != null) {
                                if (isSeriesuidInReferencedSeriesSequence(dcmObj.get(Tag.ReferencedSeriesSequence),
                                    seriesUID)) {
                                    filteredList.add(dicom);
                                }
                            }
                        }
                    }
                }
            }
        }
        return filteredList;
    }

    private static boolean isSeriesuidInReferencedSeriesSequence(DicomElement seq, String seriesUID) {
        if (seq != null && seq.vr() == VR.SQ) {
            for (int i = 0; i < seq.countItems(); ++i) {
                DicomObject dcmObj = null;
                try {
                    dcmObj = seq.getDicomObject(i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (dcmObj != null && seriesUID.equals(dcmObj.getString(Tag.SeriesInstanceUID))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSopuidInReferencedSeriesSequence(DicomElement seq, String seriesUID, String sopUID,
        Integer frameNumber) {
        if (seq != null && seq.vr() == VR.SQ) {
            for (int i = 0; i < seq.countItems(); ++i) {
                DicomObject dcmObj = null;
                try {
                    dcmObj = seq.getDicomObject(i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (dcmObj != null && seriesUID.equals(dcmObj.getString(Tag.SeriesInstanceUID))) {
                    DicomElement seq2 = dcmObj.get(Tag.ReferencedImageSequence);
                    if (seq2 != null && seq2.vr() == VR.SQ) {
                        for (int j = 0; j < seq2.countItems(); ++j) {
                            DicomObject dcmImgs = null;
                            try {
                                dcmImgs = seq2.getDicomObject(j);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (dcmImgs != null && sopUID.equals(dcmImgs.getString(Tag.ReferencedSOPInstanceUID))) {
                                if (frameNumber != null && frameNumber > 1) {
                                    int[] seqFrame = dcmImgs.getInts(Tag.ReferencedFrameNumber);
                                    if (seqFrame == null || seqFrame.length == 0) {
                                        return true;
                                    } else {
                                        for (int k : seqFrame) {
                                            if (k == frameNumber) {
                                                return true;
                                            }
                                        }
                                    }
                                } else {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 
     * @param seriesUID
     * @param specialElements
     * @return the KOSpecialElement collection for the given parameters, if the referenced seriesUID is null all the
     *         KOSpecialElement from specialElements collection are returned. In any case all the KOSpecialElement that
     *         are writable will be added to the returned collection whatever is the seriesUID. These KO are part of the
     *         new created ones by users of the application
     */
    public static final Collection<KOSpecialElement> getKoSpecialElements(
        Collection<DicomSpecialElement> specialElements, String seriesUID) {

        if (specialElements == null) {
            return null;
        }

        SortedSet<KOSpecialElement> koElementSet = null;

        for (DicomSpecialElement element : specialElements) {

            if (element instanceof KOSpecialElement) {
                KOSpecialElement koElement = (KOSpecialElement) element;
                Set<String> referencedSeriesInstanceUIDSet = koElement.getReferencedSeriesInstanceUIDSet();

                if (seriesUID == null || referencedSeriesInstanceUIDSet.contains(seriesUID) || //
                    koElement.getMediaReader().isWritableDicom()) {

                    if (koElementSet == null) {
                        // koElementSet = new TreeSet<KOSpecialElement>(ORDER_BY_DESCRIPTION);
                        koElementSet = new TreeSet<KOSpecialElement>(ORDER_BY_DATE);
                    }
                    koElementSet.add(koElement);
                }
            }
        }

        return koElementSet;
    }

    // public static final Collection<KOSpecialElement> getNewCreatedKoSpecialElements(
    // Collection<DicomSpecialElement> specialElements) {
    //
    // if (specialElements == null) {
    // return null;
    // }
    //
    // SortedSet<KOSpecialElement> koElementSet = null;
    //
    // for (DicomSpecialElement element : specialElements) {
    //
    // if (element instanceof KOSpecialElement) {
    // KOSpecialElement koElement = (KOSpecialElement) element;
    //
    // if (koElement.getMediaReader().isWritableDicom()) {
    //
    // if (koElementSet == null) {
    // koElementSet = new TreeSet<KOSpecialElement>(ORDER_BY_DATE);
    // }
    // koElementSet.add(koElement);
    // }
    // }
    // }
    //
    // return koElementSet;
    // }

    public final static SeriesComparator<DicomSpecialElement> ORDER_BY_DESCRIPTION =
        new SeriesComparator<DicomSpecialElement>() {
            @Override
            public int compare(DicomSpecialElement arg0, DicomSpecialElement arg1) {
                return String.CASE_INSENSITIVE_ORDER.compare(arg0.getLabel(), arg1.getLabel());
            }
        };

    public final static SeriesComparator<DicomSpecialElement> ORDER_BY_DATE =
        new SeriesComparator<DicomSpecialElement>() {

            @Override
            public int compare(DicomSpecialElement m1, DicomSpecialElement m2) {
                // Date date1 = (Date) m1.getTagValue(TagW.SeriesDate);
                // Date date2 = (Date) m2.getTagValue(TagW.SeriesDate);

                // Note : Dicom Standard PS3.3 - Table C.17.6-1 KEY OBJECT DOCUMENT SERIES MODULE ATTRIBUTES
                //
                // SeriesDate stands for "Date the Series started" and is optionnal parameter, don't use this to compare
                // and prefer "Content Date And Time" Tags (date and time the document content creation started)

                Date date1 = (Date) m1.getTagValue(TagW.ContentTime);
                Date date2 = (Date) m2.getTagValue(TagW.ContentTime);

                if (date1 != null && date2 != null) {
                    // inverse time
                    int comp = date1.compareTo(date2);
                    if (comp != 0) {
                        return comp;
                    }
                }

                // Note : SeriesNumber stands for a number that identifies the Series.
                // No specific semantics are specified.
                Integer val1 = (Integer) m1.getTagValue(TagW.SeriesNumber);
                Integer val2 = (Integer) m2.getTagValue(TagW.SeriesNumber);
                if (val1 != null && val2 != null) {
                    // inverse number
                    int comp = val1 > val2 ? -1 : (val1 == val2 ? 0 : 1);
                    if (comp != 0) {
                        return comp;
                    }
                }

                int comp = String.CASE_INSENSITIVE_ORDER.compare(m1.getLabel(), m2.getLabel());
                if (comp != 0) {
                    return comp;
                }

                String sopUID1 = (String) m1.getTagValue(TagW.SOPInstanceUID);
                String sopUID2 = (String) m2.getTagValue(TagW.SOPInstanceUID);

                return String.CASE_INSENSITIVE_ORDER.compare(sopUID2, sopUID1);
                // return Integer.valueOf(m1.hashCode()).compareTo(m2.hashCode());

                // TODO should implement the dicomSpecialElement Comparable and set accordingly the equals and hashcode
                // result for this Object
            }
        };

}
