/*
 * Copyright by Intland Software
 *
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Intland Software. ("Confidential Information"). You
 * shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement
 * you entered into with Intland.
 */
package com.intland.codebeamer.extensions.jira;

import static com.intland.codebeamer.extensions.jira.ChecklistForJiraMarkup.cb2checklist;
import static com.intland.codebeamer.extensions.jira.ChecklistForJiraMarkup.checklist2cb;
import static com.intland.codebeamer.manager.util.TrackerSyncConfigurationDto.NAME;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.HEADER;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.PINNED;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.unwrapChecklist;
import static com.intland.codebeamer.wiki.plugins.ChecklistPlugin.wrapChecklist;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.intland.codebeamer.controller.AbstractJsonController;
import com.intland.codebeamer.controller.jira.CustomField;
import com.intland.codebeamer.controller.jira.JiraImportController;
import com.intland.codebeamer.controller.jira.JiraTrackerSyncConfig;
import com.intland.codebeamer.manager.util.ImportStatistics;
import com.intland.codebeamer.manager.util.ImporterSupport;
import com.intland.codebeamer.manager.util.TrackerItemHistoryConfiguration;
import com.intland.codebeamer.persistence.dto.TrackerItemDto;
import com.intland.codebeamer.persistence.dto.TrackerLayoutLabelDto;
import com.intland.codebeamer.wiki.plugins.ChecklistPlugin;

/**
 * A JIRA Connector Plugin to import/export <a href=
 * "https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist
 * for JIRA<a> custom fields from/to Atlassian JIRA into/from
 * {@link ChecklistPlugin} Wiki fields
 * 
 * @author <a href="mailto:Klaus.Mehling@intland.com">Klaus Mehling</a>
 * @since Dorothy
 */
@Component("com.okapya.jira.checklist:checklist")
@CustomField(type = "WikiText", of = "Checklist")
public class ChecklistForJiraField extends AbstractJsonController {
	private static final Logger logger = Logger.getLogger(ChecklistForJiraField.class);

	public static final String RANK = "rank";
	public static final String IS_HEADER = "isHeader";
	public static final String OPTION = "option"; // ChecklistForJira V4 and older
	public static final String GLOBAL_ID = "globalItemId"; // ChecklistForJira V5 and newer
	public static final String NONE = "none";


	public static String check4ByteChars(JiraImportController controller, String string) {
		return controller != null ? controller.check4ByteChars(string) : string;
	}

	/**
	 * Convert a <a href=
	 * "https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist
	 * for JIRA<a> into a {@link ChecklistPlugin} body
	 * 
	 * @param tracker    is the JIRA tracker sync configuration
	 * @param checklist  is the checklist as returned from Jira
	 * @param controller to {@link JiraImportController#check4ByteChars(String)}
	 * @return the checklist converted into a {@link ChecklistPlugin} body
	 */
	public JsonNode jira2cb(JiraTrackerSyncConfig tracker, JsonNode checklist, JiraImportController controller) {
		if (checklist != null && checklist.isArray() && checklist.size() > 0) {
			for (JsonNode item : checklist) {
				if (item != null && item.isObject()) {
					ObjectNode itemNode = (ObjectNode) item;

					// We don't need the rank, and it's read-only anyways
					itemNode.remove(RANK);

					if (getBoolean(itemNode.remove(IS_HEADER), null)) {
						itemNode.set(HEADER, BooleanNode.TRUE);
					}

					// In Checklist for JIRA V5.0, OPTION is deprecated and replaced by OPTION_ID
					if (getInteger(item, GLOBAL_ID) != null || getBoolean(itemNode.remove(OPTION), null)) {
						itemNode.set(PINNED, BooleanNode.TRUE);
					}

					itemNode.set(NAME, TextNode.valueOf(checklist2cb(check4ByteChars(controller, getString(item, NAME)))));
				}
			}
		}

		return checklist;
	}

	/**
	 * Convert a {@link ChecklistPlugin} body into a <a href=
	 * "https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist
	 * for JIRA<a>
	 * 
	 * @param tracker   is the JIRA tracker sync configuration
	 * @param checklist is the {@link ChecklistPlugin} body
	 * @return the converted {@link ChecklistPlugin} body
	 */
	public JsonNode cb2jira(JiraTrackerSyncConfig tracker, JsonNode checklist) {
		if (checklist != null && checklist.isArray() && checklist.size() > 0) {
			for (JsonNode item : checklist) {
				if (item != null && item.isObject()) {
					ObjectNode itemNode = (ObjectNode) item;

					if (getBoolean(itemNode.remove(HEADER), null)) {
						itemNode.set(IS_HEADER, BooleanNode.TRUE);
					}

					// In Checklist for JIRA V5.0, OPTION is deprecated and replaced by OPTION_ID
					if (getBoolean(itemNode.remove(PINNED), null) && getInteger(item, GLOBAL_ID) == null) {
						itemNode.set(OPTION, BooleanNode.TRUE);
					}

					itemNode.set(NAME, TextNode.valueOf(cb2checklist(getString(item, NAME))));
				}
			}
		}

		return checklist;
	}

	/**
	 * Configure a codeBeamer Wikitext field to contain a <a href=
	 * "https://okapya.atlassian.net/wiki/spaces/CHKDOC/pages/270172389/Modifying+Checklists+using+a+REST+API">Checklist
	 * for JIRA<a>
	 * 
	 * @param field      is the Wikitext field to configure
	 * @param metaData   is the JIRA checklist field meta data
	 * @param controller to {@link JiraImportController#check4ByteChars(String)}
	 */
	@CustomField.MetaData
	public void setMetaData(TrackerLayoutLabelDto field, ObjectNode metaData, JiraImportController controller) {
		// In the JIRA REST-API schema, a checklist field is an array of checklist
		// items, but in CB it's a single value WIKI field
		field.setMultipleSelection(Boolean.FALSE);

		// The default value (options) of a checklist, are available in the
		// "allowedValues" property. Only status ID is available with Meta API's. The
		// status name is not.
		field.setDefaultValue(importChecklist(null, metaData.remove("allowedValues"), controller));

		// Place checklist on own row with full width
		field.setBreakRow(Boolean.TRUE);
		field.setColspan(Integer.valueOf(3));
	}

	/**
	 * Convert the specified checklist into {@link ChecklistPlugin} markup
	 * 
	 * @param tracker    is the JIRA tracker sync configuration
	 * @param checklist  should be a JSON array of checklist items to convert into
	 *                   {@link ChecklistPlugin} markup
	 * @param controller to {@link JiraImportController#check4ByteChars(String)}, or
	 *                   null
	 * @return the {@link ChecklistPlugin} markup for the checklist
	 */
	@CustomField.ImportFieldValue
	public String importChecklist(JiraTrackerSyncConfig tracker, JsonNode checklist, JiraImportController controller) {
		return wrapChecklist(jira2cb(tracker, checklist, controller));
	}

	/**
	 * Unwrap the checklist, that is stored in the specified Wiki markup
	 * 
	 * @param tracker is the JIRA tracker sync configuration
	 * @param markup  should be WIKI markup for this plugin
	 * @return the JSON array of checklist items as stored in the Wiki markup
	 */
	@CustomField.ExportFieldValue
	public JsonNode exportChecklist(JiraTrackerSyncConfig tracker, String markup) {
		return cb2jira(tracker, unwrapChecklist(markup));
	}

	/**
	 * Get the {@link ChecklistPlugin} body, that is stored in the specified field
	 * of the specified item
	 * 
	 * @param tracker is the tracker sync configuration
	 * @param item    is the tracker item, that contains the checklist field value,
	 *                or null, to use field default value
	 * @param field   is the Wiki field, that contains {@link ChecklistPlugin}
	 *                markup
	 * @return the {@link ChecklistPlugin} body, that is stored in the specified
	 *         field of the specified item, or null
	 */
	public static JsonNode getChecklist(JiraTrackerSyncConfig tracker, TrackerItemDto item,
			TrackerLayoutLabelDto field) {
		JsonNode checklist = null;

		if (field != null && field.isWikiTextField()) {
			if ((checklist = unwrapChecklist((String) field.getValue(item))) == null) {
				checklist = unwrapChecklist(tracker.getFieldDefaultValue(field, null));
			}
		}

		return checklist;
	}

	/*
	 * Importing checklist history is not supported.
	 * Pretend the new value was always present.
	 * History cleanup will take care of the rest.
	 * Checklist change will show up as first revision imported currently.
	 */
	@CustomField.ImportFieldChange
	public void buildHistory(TrackerItemDto item, TrackerLayoutLabelDto field, TrackerItemHistoryConfiguration fieldChange) {
		Object newValue = field.getValue(item);
		fieldChange.setOldValueObject(newValue);
		fieldChange.setNewValueObject(newValue);

	}

	@Override
	protected Logger getLogger() {
		return logger;
	}

}
