package com.agenttimeline.service;

import com.agenttimeline.model.Message;
import com.agenttimeline.model.MessageChunkEmbedding;
import com.agenttimeline.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for managing and merging overlapping chunk groups from different messages.
 *
 * This component handles the complex logic of:
 * - Detecting overlapping chunk groups
 * - Merging groups at overlapping points
 * - Removing duplicates while preserving chronological order
 * - Handling edge cases (adjacent groups, nested overlaps)
 *
 * This is a core component of Phase 5: Context-Augmented Generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkGroupManager {

    private final MessageRepository messageRepository;

    /**
     * Merge overlapping chunk groups while preserving chronological order and avoiding duplicates.
     *
     * @param expandedGroups List of expanded chunk groups to merge
     * @return Merged list of chunk groups with no overlaps
     */
    public List<ContextChunkGroup> mergeOverlappingGroups(List<ContextRetrievalService.ExpandedChunkGroup> expandedGroups) {
        if (expandedGroups == null || expandedGroups.isEmpty()) {
            return List.of();
        }

        log.debug("Starting merge of {} expanded chunk groups", expandedGroups.size());

        // Convert to ContextChunkGroups for easier manipulation
        List<ContextChunkGroup> contextGroups = expandedGroups.stream()
            .map(this::convertToContextGroup)
            .filter(Objects::nonNull)
            .collect(ArrayList::new, (list, group) -> list.add(group), ArrayList::addAll);

        if (contextGroups.isEmpty()) {
            log.debug("No valid context groups after conversion");
            return List.of();
        }

        // Sort groups by their earliest timestamp for consistent processing
        contextGroups.sort(Comparator.comparing(ContextChunkGroup::getEarliestTimestamp));

        // Iteratively merge overlapping groups
        List<ContextChunkGroup> mergedGroups = new ArrayList<>();
        Set<ContextChunkGroup> processedGroups = new HashSet<>();

        for (ContextChunkGroup currentGroup : contextGroups) {
            if (processedGroups.contains(currentGroup)) {
                continue;
            }

            List<ContextChunkGroup> overlappingGroups = findOverlappingGroups(currentGroup, contextGroups);
            if (overlappingGroups.size() > 1) {
                // Merge all overlapping groups
                ContextChunkGroup mergedGroup = mergeGroups(overlappingGroups);
                mergedGroups.add(mergedGroup);

                // Mark all overlapping groups as processed
                processedGroups.addAll(overlappingGroups);

                log.debug("Merged {} overlapping groups into single group with {} total chunks",
                    overlappingGroups.size(), mergedGroup.getTotalChunks());
            } else {
                // No overlaps, add as-is
                mergedGroups.add(currentGroup);
                processedGroups.add(currentGroup);
            }
        }

        // Sort final groups by timestamp for consistent ordering
        mergedGroups.sort(Comparator.comparing(ContextChunkGroup::getEarliestTimestamp));

        log.info("Successfully merged {} expanded groups into {} final groups",
            expandedGroups.size(), mergedGroups.size());

        return mergedGroups;
    }

    /**
     * Find all groups that overlap with the given group.
     */
    private List<ContextChunkGroup> findOverlappingGroups(ContextChunkGroup targetGroup,
                                                        List<ContextChunkGroup> allGroups) {
        List<ContextChunkGroup> overlapping = new ArrayList<>();
        overlapping.add(targetGroup); // Always include the target group

        for (ContextChunkGroup otherGroup : allGroups) {
            if (targetGroup.equals(otherGroup)) {
                continue; // Skip self-comparison
            }

            if (groupsOverlap(targetGroup, otherGroup)) {
                overlapping.add(otherGroup);
            }
        }

        return overlapping;
    }

    /**
     * Check if two chunk groups overlap based on their message timestamps and content.
     */
    private boolean groupsOverlap(ContextChunkGroup group1, ContextChunkGroup group2) {
        // Check if messages are from different sources but have overlapping timestamps
        if (!group1.getMessageId().equals(group2.getMessageId())) {
            // Different messages - check for timestamp overlap
            return timestampsOverlap(group1, group2) && contentSimilar(group1, group2);
        }

        // Same message - always overlap (they're from the same source)
        return true;
    }

    /**
     * Check if two groups have overlapping timestamps.
     */
    private boolean timestampsOverlap(ContextChunkGroup group1, ContextChunkGroup group2) {
        // If either group has no timestamp info, assume they might overlap
        if (group1.getEarliestTimestamp() == null || group2.getEarliestTimestamp() == null) {
            return true;
        }

        // Check for timestamp overlap (allowing for some tolerance)
        long toleranceMs = 1000; // 1 second tolerance for timestamp precision issues
        long group1Start = group1.getEarliestTimestamp().getTime();
        long group1End = group1.getLatestTimestamp().getTime() + toleranceMs;
        long group2Start = group2.getEarliestTimestamp().getTime();
        long group2End = group2.getLatestTimestamp().getTime() + toleranceMs;

        return !(group1End < group2Start || group2End < group1Start);
    }

    /**
     * Check if two groups have similar content (basic heuristic).
     */
    private boolean contentSimilar(ContextChunkGroup group1, ContextChunkGroup group2) {
        String text1 = group1.getCombinedText().toLowerCase();
        String text2 = group2.getCombinedText().toLowerCase();

        // Simple similarity check: if they share significant common words
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));

        // Find intersection
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        // Calculate Jaccard similarity
        int unionSize = words1.size() + words2.size() - intersection.size();
        double similarity = unionSize > 0 ? (double) intersection.size() / unionSize : 0.0;

        return similarity > 0.3; // 30% word overlap threshold
    }

    /**
     * Merge multiple overlapping groups into a single group.
     */
    private ContextChunkGroup mergeGroups(List<ContextChunkGroup> groupsToMerge) {
        if (groupsToMerge.size() == 1) {
            return groupsToMerge.get(0);
        }

        // Collect all chunks from all groups
        Map<String, MessageChunkEmbedding> allChunks = new HashMap<>();
        Date earliestTimestamp = null;
        Date latestTimestamp = null;

        for (ContextChunkGroup group : groupsToMerge) {
            for (MessageChunkEmbedding chunk : group.getChunks()) {
                // Use chunk ID as key to avoid duplicates
                String chunkKey = chunk.getId().toString();
                allChunks.put(chunkKey, chunk);

                // Track timestamps
                if (chunk.getCreatedAt() != null) {
                    Date chunkDate = Date.from(chunk.getCreatedAt().toInstant(java.time.ZoneOffset.UTC));
                    if (earliestTimestamp == null || chunkDate.before(earliestTimestamp)) {
                        earliestTimestamp = chunkDate;
                    }
                    if (latestTimestamp == null || chunkDate.after(latestTimestamp)) {
                        latestTimestamp = chunkDate;
                    }
                }
            }
        }

        // Sort chunks chronologically
        List<MessageChunkEmbedding> sortedChunks = allChunks.values().stream()
            .sorted(Comparator.comparing(
                (MessageChunkEmbedding c) -> c.getCreatedAt() != null ?
                    Date.from(c.getCreatedAt().toInstant(java.time.ZoneOffset.UTC)) :
                    new Date(0))
                .thenComparing(MessageChunkEmbedding::getChunkIndex))
            .collect(ArrayList::new, (list, chunk) -> list.add(chunk), ArrayList::addAll);

        // Create merged group - use the message ID and role from the earliest group
        ContextChunkGroup earliestGroup = groupsToMerge.stream()
            .min(Comparator.comparing(
                (ContextChunkGroup g) -> g.getEarliestTimestamp() != null ? g.getEarliestTimestamp() : new Date(Long.MAX_VALUE),
                Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(groupsToMerge.get(0));

        String mergedMessageId = earliestGroup.getMessageId();
        Message.Role mergedRole = earliestGroup.getRole();

        return new ContextChunkGroup(mergedMessageId, sortedChunks, earliestTimestamp, latestTimestamp, mergedRole);
    }

    /**
     * Convert an ExpandedChunkGroup to a ContextChunkGroup with timestamp information.
     */
    private ContextChunkGroup convertToContextGroup(ContextRetrievalService.ExpandedChunkGroup expandedGroup) {
        try {
            // Get message timestamp from repository
            Optional<Message> messageOpt = messageRepository.findById(expandedGroup.getMessageId());
            if (messageOpt.isEmpty()) {
                log.warn("Message {} not found in repository", expandedGroup.getMessageId());
                return null;
            }

            Message message = messageOpt.get();
            Date messageTimestamp = Date.from(message.getTimestamp().toInstant(java.time.ZoneOffset.UTC));

            // All chunks in a group have the same timestamp (from same message)
            return new ContextChunkGroup(
                expandedGroup.getMessageId(),
                expandedGroup.getChunks(),
                messageTimestamp,
                messageTimestamp,
                message.getRole()
            );

        } catch (Exception e) {
            log.error("Error converting expanded group for message {}: {}",
                expandedGroup.getMessageId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Enhanced chunk group with timestamp information for merging operations.
     */
    public static class ContextChunkGroup {
        private final String messageId;
        private final List<MessageChunkEmbedding> chunks;
        private final Date earliestTimestamp;
        private final Date latestTimestamp;
        private final Message.Role role;

        public ContextChunkGroup(String messageId, List<MessageChunkEmbedding> chunks,
                               Date earliestTimestamp, Date latestTimestamp, Message.Role role) {
            this.messageId = messageId;
            this.chunks = new ArrayList<>(chunks);
            this.earliestTimestamp = earliestTimestamp;
            this.latestTimestamp = latestTimestamp;
            this.role = role;
        }

        public String getMessageId() {
            return messageId;
        }

        public List<MessageChunkEmbedding> getChunks() {
            return new ArrayList<>(chunks);
        }

        public Date getEarliestTimestamp() {
            return earliestTimestamp;
        }

        public Date getLatestTimestamp() {
            return latestTimestamp;
        }

        public Message.Role getRole() {
            return role;
        }

        public int getTotalChunks() {
            return chunks.size();
        }

        public String getCombinedText() {
            StringBuilder sb = new StringBuilder();
            for (MessageChunkEmbedding chunk : chunks) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(chunk.getChunkText());
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContextChunkGroup that = (ContextChunkGroup) o;
            return Objects.equals(messageId, that.messageId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageId);
        }

        @Override
        public String toString() {
            return "ContextChunkGroup{" +
                "messageId='" + messageId + '\'' +
                ", chunkCount=" + chunks.size() +
                ", timestampRange=[" + earliestTimestamp + " to " + latestTimestamp + "]" +
                '}';
        }
    }
}
