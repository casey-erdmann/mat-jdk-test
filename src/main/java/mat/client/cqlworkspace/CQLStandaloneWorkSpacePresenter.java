package mat.client.cqlworkspace;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.gwtbootstrap3.client.ui.constants.ValidationState;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import edu.ycp.cs.dh.acegwt.client.ace.AceAnnotationType;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditor;
import mat.client.CqlComposerPresenter;
import mat.client.Mat;
import mat.client.MatPresenter;
import mat.client.clause.event.QDSElementCreatedEvent;
import mat.client.codelist.service.SaveUpdateCodeListResult;
import mat.client.cqlworkspace.codes.CQLCodesView.Delegator;
import mat.client.cqlworkspace.functions.CQLFunctionsView.Observer;
import mat.client.cqlworkspace.generalinformation.CQLGeneralInformationUtility;
import mat.client.cqlworkspace.includedlibrary.CQLIncludeLibraryView;
import mat.client.cqlworkspace.valuesets.CQLAppliedValueSetUtility;
import mat.client.cqlworkspace.valuesets.CQLAppliedValueSetView;
import mat.client.event.CQLLibrarySelectedEvent;
import mat.client.inapphelp.message.InAppHelpMessages;
import mat.client.measure.service.CQLLibraryServiceAsync;
import mat.client.measure.service.SaveCQLLibraryResult;
import mat.client.shared.CQLWorkSpaceConstants;
import mat.client.shared.MatContext;
import mat.client.shared.MessageDelegate;
import mat.client.shared.ValueSetNameInputValidator;
import mat.client.umls.service.VsacApiResult;
import mat.model.CQLValueSetTransferObject;
import mat.model.CodeListSearchDTO;
import mat.model.GlobalCopyPasteObject;
import mat.model.MatCodeTransferObject;
import mat.model.MatValueSet;
import mat.model.clause.QDSAttributes;
import mat.model.cql.CQLCode;
import mat.model.cql.CQLDefinition;
import mat.model.cql.CQLFunctionArgument;
import mat.model.cql.CQLFunctions;
import mat.model.cql.CQLIncludeLibrary;
import mat.model.cql.CQLLibraryDataSetObject;
import mat.model.cql.CQLParameter;
import mat.model.cql.CQLQualityDataModelWrapper;
import mat.model.cql.CQLQualityDataSetDTO;
import mat.shared.CQLError;
import mat.shared.CQLIdentifierObject;
import mat.shared.ConstantMessages;
import mat.shared.GetUsedCQLArtifactsResult;
import mat.shared.SaveUpdateCQLResult;
import mat.shared.StringUtility;
import mat.shared.cql.error.InvalidLibraryException;

public class CQLStandaloneWorkSpacePresenter extends AbstractCQLWorkspacePresenter implements MatPresenter {
	private SimplePanel emptyWidget = new SimplePanel();
	private boolean isCQLWorkSpaceLoaded = false;
	private final CQLLibraryServiceAsync cqlService = MatContext.get().getCQLLibraryService();
	private static final String ERROR_INCLUDE_ALIAS_NAME_NO_SPECIAL_CHAR  = "Invalid Library Alias. Must be unique, start with an alpha-character or underscore followed by an alpha-numeric character(s) or underscore(s), and must not contain spaces.";

	public CQLStandaloneWorkSpacePresenter(final CQLWorkspaceView srchDisplay) {
		cqlWorkspaceView = srchDisplay;
		setInAppHelpMessages();
		emptyWidget.add(new Label("No CQL Library Selected"));
		addEventHandlers();
	}

	private void setInAppHelpMessages() {
		cqlWorkspaceView.getCqlGeneralInformationView().getInAppHelp().setMessage(InAppHelpMessages.STANDALONE_CQL_LIBRARY_GENERAL_INFORMATION);
		cqlWorkspaceView.getValueSetView().getInAppHelp().setMessage(InAppHelpMessages.STANDALONE_CQL_LIBRARY_VALUE_SET);
		cqlWorkspaceView.getCQLParametersView().getInAppHelp().setMessage(InAppHelpMessages.STANDALONE_CQL_LIBRARY_PARAMETER);
		cqlWorkspaceView.getCQLFunctionsView().getInAppHelp().setMessage(InAppHelpMessages.STANDALONE_CQL_LIBRARY_FUNCTION);
		cqlWorkspaceView.getIncludeView().getInAppHelp().setMessage(InAppHelpMessages.STANDALONE_CQL_LIBRARY_INCLUDES);
		cqlWorkspaceView.getCodesView().getInAppHelp().setMessage(InAppHelpMessages.STANDALONE_CQL_LIBRARY_CODES);
		cqlWorkspaceView.getCQLDefinitionsView().getInAppHelp().setMessage(InAppHelpMessages.STANDALONE_CQL_LIBRARY_DEFINITION);
		cqlWorkspaceView.getCQLLibraryEditorView().getInAppHelp().setMessage(InAppHelpMessages.STANDALONE_CQL_LIBRARY_VIEW_CQL);
	}

	private void addEventHandlers() {
		MatContext.get().getEventBus().addHandler(CQLLibrarySelectedEvent.TYPE, new CQLLibrarySelectedEvent.Handler() {
			@Override
			public void onLibrarySelected(CQLLibrarySelectedEvent event) {
				isCQLWorkSpaceLoaded = false;
				if (event.getCqlLibraryId() != null) {
					isCQLWorkSpaceLoaded = true;
					logRecentActivity();
				} else {
					displayEmpty();
				}
			}
		});

		getDeleteConfirmationDialogBoxYesButton().addClickHandler(event -> deleteConfirmationYesClicked());
		getDeleteConfirmationDialogBoxNoButton().addClickHandler(event -> deleteConfirmationNoClicked());

		ClickHandler cHandler = new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().detach();
				cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().detach();
				cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().detach();
			}
		};
		cqlWorkspaceView.getMainPanel().addDomHandler(cHandler, ClickEvent.getType());
		addCodeSearchPanelHandlers();
		addDefineEventHandlers();
		addEventHandlerOnAceEditors();
		addFunctionEventHandlers();
		addGeneralInfoEventHandlers();
		addIncludeCQLLibraryHandlers();
		addParameterEventHandlers();
		addValueSetEventHandlers();
		addCQLLibraryEditorViewHandlers();
		addWarningConfirmationHandlers();
	}

	private void deleteValueSet(String toBeDeletedValueSetId) {
		showSearchingBusy(true);
		MatContext.get().getCQLLibraryService().deleteValueSet(toBeDeletedValueSetId, MatContext.get().getCurrentCQLLibraryId(), new AsyncCallback<SaveUpdateCQLResult>() {

			@Override
			public void onFailure(final Throwable caught) {
				Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
				showSearchingBusy(false);
			}

			@Override
			public void onSuccess(final SaveUpdateCQLResult result) {
				successfullyDeletedValueSet(result);
			}
		});
	}

	@Override
	protected void getAppliedValuesetAndCodeList() {
		showSearchingBusy(true);
		String cqlLibraryId = MatContext.get().getCurrentCQLLibraryId();
		if ((cqlLibraryId != null) && !cqlLibraryId.equals(EMPTY_STRING)) {
			MatContext.get().getLibraryService().getCQLData(cqlLibraryId, new AsyncCallback<SaveUpdateCQLResult>() {

				@Override
				public void onFailure(Throwable caught) {
					Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
					showSearchingBusy(false);

				}

				@Override
				public void onSuccess(SaveUpdateCQLResult result) {
					List<CQLQualityDataSetDTO> valuesets = result.getCqlModel().getAllValueSetAndCodeList().stream().filter(v -> (
							(v.getOriginalCodeListName() != null &&
							!v.getOriginalCodeListName().isEmpty()) 
							|| (v.getCodeIdentifier() != null && !v.getCodeIdentifier().isEmpty()))
					).collect(Collectors.toList());
					setAppliedValueSetListInTable(valuesets);
					showSearchingBusy(false);
				}
			});
		}
	}

	private void addParameterEventHandlers() {
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterNameListBox().addKeyPressHandler(event -> listBoxKeyPress(event));
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterNameListBox().addDoubleClickHandler(event -> leftNavParameterNameListBoxDoubleClickEvent(event));
		cqlWorkspaceView.getCQLParametersView().getPanelViewCQLCollapse().addShowHandler(event -> parameterShowEvent());
		cqlWorkspaceView.getCQLParametersView().getPanelViewCQLCollapse().addHideHandler(event -> parameterHideEvent());
		cqlWorkspaceView.getCQLParametersView().getParameterButtonBar().getSaveButton().addClickHandler(event -> parameterSaveClicked());
		cqlWorkspaceView.getCQLParametersView().getParameterButtonBar().getEraseButton().addClickHandler(event -> parameterEraseClicked());
		cqlWorkspaceView.getCQLParametersView().getParameterButtonBar().getDeleteButton().addClickHandler(event -> parameterDeleteClicked());
		cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea().addKeyUpHandler(event -> keyUpEvent());
		cqlWorkspaceView.getCQLParametersView().getParameterCommentTextArea().addKeyUpHandler(event -> keyUpEvent());
		cqlWorkspaceView.getCQLParametersView().getAddNewButtonBar().getaddNewButton().addClickHandler(event -> parameterAddNewClicked());
		cqlWorkspaceView.getCQLParametersView().getParameterCommentTextArea().addBlurHandler(event -> parameterCommentBlurEvent());
	}

	@Override
	protected void showCompleteCQL(final AceEditor aceEditor) {
		MatContext.get().getCQLLibraryService().getLibraryCQLFileData(MatContext.get().getCurrentCQLLibraryId(), new AsyncCallback<SaveUpdateCQLResult>() {
			@Override
			public void onSuccess(SaveUpdateCQLResult result) {
				String formattedName = result.getCqlModel().getFormattedName();
				if (result.isSuccess()) {
					if ((result.getCqlString() != null) && !result.getCqlString().isEmpty()) {
						aceEditor.clearAnnotations();
						aceEditor.redisplay();

						if(result.getLibraryNameErrorsMap().get(formattedName) != null) {
							for (CQLError error : result.getLibraryNameErrorsMap().get(formattedName)) {
								int line = error.getErrorInLine();
								int column = error.getErrorAtOffeset();
								aceEditor.addAnnotation(line - 1, column, error.getErrorMessage(), AceAnnotationType.ERROR);
							}
						}

						if(result.getLibraryNameWarningsMap().get(formattedName) != null) {
							for (CQLError warning : result.getLibraryNameWarningsMap().get(formattedName)) {
								int line = warning.getErrorInLine(); 
								int column = warning.getErrorAtOffeset();
								aceEditor.addAnnotation(line - 1, column, warning.getErrorMessage(), AceAnnotationType.WARNING);
							}	
						}

						aceEditor.setText(result.getCqlString());
						aceEditor.setAnnotations();
						aceEditor.gotoLine(1);
						aceEditor.redisplay();
					}
				}
			}

			@Override
			public void onFailure(Throwable caught) {
				Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
			}
		});
	}

	private void addDefineEventHandlers() {
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefineNameListBox().addKeyPressHandler(event -> cqlLeftNavBarDefineNameListBoxKeyPressed(event));
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefineNameListBox().addDoubleClickHandler(event -> cqlLeftNavBarDefineNameListBoxDoubleClickEvent(event));
		cqlWorkspaceView.getCQLDefinitionsView().getPanelViewCQLCollapse().addShowHandler(event -> definitionShowEvent());
		cqlWorkspaceView.getCQLDefinitionsView().getPanelViewCQLCollapse().addHideHandler(event -> definitionHideEvent());
		cqlWorkspaceView.getCQLDefinitionsView().getDefineButtonBar().getInsertButton().addClickHandler(event -> buildInsertPopUp());
		cqlWorkspaceView.getCQLDefinitionsView().getDefineButtonBar().getSaveButton().addClickHandler(event -> definitionSaveClicked());
		cqlWorkspaceView.getCQLDefinitionsView().getDefineButtonBar().getEraseButton().addClickHandler(event -> definitionEraseClicked());
		cqlWorkspaceView.getCQLDefinitionsView().getDefineButtonBar().getDeleteButton().addClickHandler(event -> definitionDeleteButtonClicked());
		cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea().addKeyUpHandler(event -> keyUpEvent());
		cqlWorkspaceView.getCQLDefinitionsView().getDefineCommentTextArea().addKeyUpHandler(event -> keyUpEvent());
		cqlWorkspaceView.getCQLDefinitionsView().getAddNewButtonBar().getaddNewButton().addClickHandler(event -> definitionAddNewClicked());
		cqlWorkspaceView.getCQLDefinitionsView().getDefineCommentTextArea().addBlurHandler(event -> definitionCommentBlurEvent());
		cqlWorkspaceView.getCQLDefinitionsView().getDefineButtonBar().getExpressionBuilderButton().addClickHandler(event -> expressionBuilderButtonClicked());
	}

	private void addFunctionEventHandlers() {
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getFuncNameListBox().addKeyPressHandler(event -> leftNavBarFuncNameListBoxKeyPressedEvent(event));
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getFuncNameListBox().addDoubleClickHandler(event -> leftNavBarFuncNameListBoxDoubleClickedEvent(event));
		cqlWorkspaceView.getCQLFunctionsView().getPanelViewCQLCollapse().addShowHandler(event -> functionShowEvent());
		cqlWorkspaceView.getCQLFunctionsView().getPanelViewCQLCollapse().addHideHandler(event -> functionHideEvent());
		cqlWorkspaceView.getCQLFunctionsView().getFunctionButtonBar().getInsertButton().addClickHandler(event -> functionsViewInsertButtonClicked());
		cqlWorkspaceView.getCQLFunctionsView().getFunctionButtonBar().getSaveButton().addClickHandler(event -> functionSaveClicked());
		cqlWorkspaceView.getCQLFunctionsView().getFunctionButtonBar().getEraseButton().addClickHandler(event -> functionEraseClicked());
		cqlWorkspaceView.getCQLFunctionsView().getAddNewArgument().addClickHandler(event -> addNewArgumentClicked());
		cqlWorkspaceView.getCQLFunctionsView().getFunctionButtonBar().getDeleteButton().addClickHandler(event -> functionDeleteClicked());
		cqlWorkspaceView.getCQLFunctionsView().setObserver(new Observer() {
			@Override
			public void onModifyClicked(CQLFunctionArgument result) {
				setIsPageDirty(true);
				cqlWorkspaceView.resetMessageDisplay();
				if (result.getArgumentType().equalsIgnoreCase(CQLWorkSpaceConstants.CQL_MODEL_DATA_TYPE)) {
					getAttributesForDataType(result);
				} else {
					AddFunctionArgumentDialogBox.showArgumentDialogBox(result, true,cqlWorkspaceView.getCQLFunctionsView(), messagePanel, hasEditPermissions());
				}
			}

			@Override
			public void onDeleteClicked(CQLFunctionArgument result, int index) {
				cqlWorkspaceView.getCQLFunctionsView().getFunctionButtonBar().getInfoButtonGroup().getElement().setAttribute("class", "btn-group");
				cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionArgumentObjId(result.getId());
				cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionArgumentName(result.getArgumentName());
				deleteConfirmationDialogBox.getMessageAlert().createAlert(buildSelectedToDeleteWithConfirmationMessage(ARGUMENT, result.getArgumentName()));
				deleteConfirmationDialogBox.show();
				cqlWorkspaceView.getCQLFunctionsView().getMainFunctionVerticalPanel().setFocus(true);
			}
		});
		cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().addKeyUpHandler(event -> keyUpEvent());
		cqlWorkspaceView.getCQLFunctionsView().getFunctionCommentTextArea().addKeyUpHandler(event -> keyUpEvent());
		cqlWorkspaceView.getCQLFunctionsView().getAddNewButtonBar().getaddNewButton().addClickHandler(event -> addNewFunctionClicked());
		cqlWorkspaceView.getCQLFunctionsView().getFunctionCommentTextArea().addBlurHandler(event -> functionCommentBlurEvent());
	}

	@Override
	protected void createAddArgumentViewForFunctions() {
		((CQLStandaloneWorkSpaceView)cqlWorkspaceView).createAddArgumentViewForFunctions(new ArrayList<CQLFunctionArgument>());
	}

	private void addGeneralInfoEventHandlers() {
		cqlWorkspaceView.getCqlGeneralInformationView().getSaveButton().addClickHandler(event -> saveCQLGeneralInfo());
		cqlWorkspaceView.getCqlGeneralInformationView().getLibraryNameTextBox().addKeyUpHandler(event -> resetMessagesAndSetPageDirty(true));
		cqlWorkspaceView.getCqlGeneralInformationView().getCommentsTextBox().addKeyUpHandler(event -> keyUpEvent());
		cqlWorkspaceView.getCqlGeneralInformationView().getCommentsTextBox().addBlurHandler(event -> generalCommentBlurEvent());
	}
	
	private void saveCQLGeneralInfo() {
		
		if(hasEditPermissions()) {
			resetMessagesAndSetPageDirty(false);
			String libraryName = cqlWorkspaceView.getCqlGeneralInformationView().getLibraryNameTextBox().getText().trim();
			String commentContent = cqlWorkspaceView.getCqlGeneralInformationView().getCommentsTextBox().getText().trim();
			boolean isValid = CQLGeneralInformationUtility.validateGeneralInformationSection(cqlWorkspaceView.getCqlGeneralInformationView(), messagePanel, libraryName, commentContent);
			if(isValid) {
				saveCQLGeneralInformation(libraryName, commentContent);
			}
		}
		

	}

	private void addIncludeCQLLibraryHandlers() {
		cqlWorkspaceView.getIncludeView().getSaveModifyButton().addClickHandler(event -> includeViewSaveModifyClicked());
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox().addKeyPressHandler(event -> listBoxKeyPress(cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox(), event));
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox().addDoubleClickHandler(event -> cqlLeftNavBarIncludesNameListBoxDoubleClickEvent(event));
		cqlWorkspaceView.getIncludeView().getSearchButton().addClickHandler(event -> includeViewSearchButtonClicked());
		cqlWorkspaceView.getIncludeView().getFocusPanel().addKeyDownHandler(event -> includeFocusPanelKeyDown(event));
		cqlWorkspaceView.getIncludeView().getIncludesButtonBar().getSaveButton().addClickHandler(event -> includesViewSaveClicked());
		cqlWorkspaceView.getIncludeView().getDeleteButton().addClickHandler(event -> includesViewDeleteButtonClicked());
		cqlWorkspaceView.getIncludeView().getCloseButton().addClickHandler(event -> includeViewCloseButtonClicked());
		cqlWorkspaceView.getIncludeView().getEraseButton().addClickHandler(event -> includeViewEraseButtonClicked());
		cqlWorkspaceView.getIncludeView().getAliasNameTxtArea().addValueChangeHandler(event -> aliasNameChangeHandler());
		cqlWorkspaceView.getIncludeView().setObserver(new CQLIncludeLibraryView.Observer() {

			@Override
			public void onCheckBoxClicked(CQLLibraryDataSetObject result) {
				setIsPageDirty(true);
				MatContext.get().getCQLLibraryService().findCQLLibraryByID(result.getId(),
						new AsyncCallback<CQLLibraryDataSetObject>() {

					@Override
					public void onFailure(Throwable caught) {
						Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
						Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
					}

					@Override
					public void onSuccess(CQLLibraryDataSetObject result) {
						cqlWorkspaceView.getIncludeView().getViewCQLEditor().setText(result.getCqlText());
					}
				});
			}
		});
	}

	protected void addAndModifyFunction() {
		cqlWorkspaceView.resetMessageDisplay();
		final String functionName = cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().getText();
		String functionBody = cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().getText();
		String functionComment = cqlWorkspaceView.getCQLFunctionsView().getFunctionCommentTextArea().getText();

		boolean isValidFunctionName = isValidExpressionName(functionName);
		if (isValidFunctionName) {
			if (validator.hasSpecialCharacter(functionName.trim())) {
				cqlWorkspaceView.getCQLFunctionsView().getFuncNameGroup().setValidationState(ValidationState.ERROR);
				messagePanel.getErrorMessageAlert().createAlert(ERROR_FUNCTION_NAME_NO_SPECIAL_CHAR);
				cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().setText(functionName.trim());	
			} else if(validator.isCommentMoreThan250Characters(functionComment)) {
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getERROR_VALIDATION_COMMENT_AREA());
				cqlWorkspaceView.getCQLFunctionsView().getFuncCommentGroup().setValidationState(ValidationState.ERROR);
			} else if(validator.doesCommentContainInvalidCharacters(functionComment)){
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getINVALID_COMMENT_CHARACTERS());
				cqlWorkspaceView.getCQLFunctionsView().getFuncCommentGroup().setValidationState(ValidationState.ERROR);
			} else {
				CQLFunctions function = new CQLFunctions();
				function.setLogic(functionBody);
				function.setName(functionName);
				function.setCommentString(functionComment);
				function.setArgumentList(cqlWorkspaceView.getCQLFunctionsView().getFunctionArgumentList());
				CQLFunctions toBeModifiedParamObj = null;

				if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedFunctionObjId() != null) {
					toBeModifiedParamObj = cqlWorkspaceView.getCQLLeftNavBarPanelView().getFunctionMap()
							.get(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedFunctionObjId());
				}
				showSearchingBusy(true);
				MatContext.get().getCQLLibraryService().saveAndModifyFunctions(
						MatContext.get().getCurrentCQLLibraryId(), toBeModifiedParamObj, function,
						cqlWorkspaceView.getCQLLeftNavBarPanelView().getViewFunctions(),
						isFormattable, new AsyncCallback<SaveUpdateCQLResult>() {

							@Override
							public void onFailure(Throwable caught) {
								cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionObjId(null);
								cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionArgumentObjId(null);
								cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionArgumentName(null);
								messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
								showSearchingBusy(false);
							}

							@Override
							public void onSuccess(SaveUpdateCQLResult result) {
								if (result != null) {
									if (result.isSuccess()) {
										cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewFunctions(result.getCqlModel().getCqlFunctions());
										MatContext.get().setFuncs(getFunctionList(result.getCqlModel().getCqlFunctions()));
										MatContext.get().setExpressionToReturnTypeMap(result.getUsedCQLArtifacts().getExpressionToReturnTypeMap());
										cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionObjId(result.getFunction().getId());
										cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddFunctionsNamesToListBox();
										cqlWorkspaceView.getCQLLeftNavBarPanelView().updateFunctionMap();
										messagePanel.getErrorMessageAlert().clearAlert();
										messagePanel.getSuccessMessageAlert().setVisible(true);
										cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().setText(result.getFunction().getName());
										cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().replace(result.getFunction().getLogic());
										setIsPageDirty(false);
										cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().clearAnnotations();
										cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().removeAllMarkers();
										MatContext.get().setCQLModel(result.getCqlModel());
										if (SharedCQLWorkspaceUtility.validateCQLArtifact(result, cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor(), messagePanel, FUNCTION, functionName.trim())) {
											cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
											cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression");
										} else if (!result.isDatatypeUsedCorrectly()) {
											if(result.isValidCQLWhileSavingExpression()){
												cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setText(result.getFunction().getReturnType());
												cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression is " + result.getFunction().getReturnType());
											} else {
												cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
												cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression");
											}
										} else {
											if(result.isValidCQLWhileSavingExpression()){
												cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setText(result.getFunction().getReturnType());
												cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression is " + result.getFunction().getReturnType());
											} else {
												cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
												cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression");
											}
										}
										cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().setAnnotations();
										cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().redisplay();
									} else if (result.getFailureReason() == SaveUpdateCQLResult.NAME_NOT_UNIQUE) {
										displayErrorMessage(ERROR_DUPLICATE_IDENTIFIER_NAME, functionName, cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea());
									} else if (result.getFailureReason() == SaveUpdateCQLResult.NODE_NOT_FOUND) {
										displayErrorMessage(UNABLE_TO_FIND_NODE_TO_MODIFY, functionName, cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea());
									} else if (result.getFailureReason() == SaveUpdateCQLResult.NO_SPECIAL_CHAR) {
										displayErrorMessage(ERROR_FUNCTION_NAME_NO_SPECIAL_CHAR, functionName, cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea());
										if (result.getFunction() != null) {
											((CQLStandaloneWorkSpaceView)cqlWorkspaceView).createAddArgumentViewForFunctions(
													result.getFunction().getArgumentList());
										}
									} else if (result.getFailureReason() == SaveUpdateCQLResult.FUNCTION_ARGUMENT_INVALID) {
										messagePanel.getSuccessMessageAlert().clearAlert();
										messagePanel.getErrorMessageAlert().createAlert(MessageDelegate.CQL_FUNCTION_ARGUMENT_NAME_ERROR);
									} else if (result.getFailureReason() == SaveUpdateCQLResult.COMMENT_INVALID) {
										messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getERROR_VALIDATION_COMMENT_AREA());
									}
								}
								
								showSearchingBusy(false);
							}
						});
			}
		} else {
			cqlWorkspaceView.getCQLFunctionsView().getFuncNameGroup().setValidationState(ValidationState.ERROR);
			messagePanel.getErrorMessageAlert().createAlert(functionName.isEmpty() ? ERROR_SAVE_CQL_FUNCTION : "Invalid Function name. " + DEFINED_KEYWORD_EXPRESION_ERROR_MESSAGE);
			cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().setText(functionName.trim());
		}
	}

	@Override
	protected void addAndModifyParameters() {
		cqlWorkspaceView.resetMessageDisplay();
		final String parameterName = cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea().getText();
		String parameterLogic = cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().getText();
		String parameterComment = cqlWorkspaceView.getCQLParametersView().getParameterCommentTextArea().getText();

		boolean isValidParamaterName = isValidExpressionName(parameterName);
		if (isValidParamaterName) {
			if (validator.hasSpecialCharacter(parameterName.trim())) {
				cqlWorkspaceView.getCQLParametersView().getParamNameGroup().setValidationState(ValidationState.ERROR);
				messagePanel.getErrorMessageAlert().createAlert(ERROR_PARAMETER_NAME_NO_SPECIAL_CHAR);
				cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea().setText(parameterName.trim());
			} else if(validator.isCommentMoreThan250Characters(parameterComment)){
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getERROR_VALIDATION_COMMENT_AREA());
				cqlWorkspaceView.getCQLParametersView().getParamCommentGroup().setValidationState(ValidationState.ERROR);
			} else if(validator.doesCommentContainInvalidCharacters(parameterComment)){
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getINVALID_COMMENT_CHARACTERS());
				cqlWorkspaceView.getCQLParametersView().getParamCommentGroup().setValidationState(ValidationState.ERROR);
			} else {
				CQLParameter parameter = new CQLParameter();
				parameter.setLogic(parameterLogic);
				parameter.setName(parameterName);
				parameter.setCommentString(parameterComment);
				CQLParameter toBeModifiedParamObj = null;

				if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedParamerterObjId() != null) {
					toBeModifiedParamObj = cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterMap().get(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedParamerterObjId());
				}
				showSearchingBusy(true);
				MatContext.get().getCQLLibraryService().saveAndModifyParameters(
						MatContext.get().getCurrentCQLLibraryId(), toBeModifiedParamObj, parameter,
						cqlWorkspaceView.getCQLLeftNavBarPanelView().getViewParameterList(),
						isFormattable, new AsyncCallback<SaveUpdateCQLResult>() {

							@Override
							public void onFailure(Throwable caught) {
								cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedParamerterObjId(null);
								messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
								showSearchingBusy(false);
							}

							@Override
							public void onSuccess(SaveUpdateCQLResult result) {
								if (result != null) {
									if (result.isSuccess()) {
										cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewParameterList(result.getCqlModel().getCqlParameters());
										MatContext.get().setParameters(getParameterList(result.getCqlModel().getCqlParameters()));
										MatContext.get().setExpressionToReturnTypeMap(result.getUsedCQLArtifacts().getExpressionToReturnTypeMap());
										MatContext.get().setCQLModel(result.getCqlModel());
										cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedParamerterObjId(result.getParameter().getId());
										cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddParameterNamesToListBox();
										cqlWorkspaceView.getCQLLeftNavBarPanelView().updateParamMap();
										messagePanel.getErrorMessageAlert().clearAlert();
										cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea().setText(result.getParameter().getName());
										cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().replace(result.getParameter().getLogic());
										setIsPageDirty(false);
										cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().clearAnnotations();
										cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().removeAllMarkers();
										SharedCQLWorkspaceUtility.validateCQLArtifact(result, cqlWorkspaceView.getCQLParametersView().getParameterAceEditor(), messagePanel, PARAMETER, parameterName.trim());
										cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().setAnnotations();
										cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().redisplay();
									} else if (result.getFailureReason() == SaveUpdateCQLResult.NAME_NOT_UNIQUE) {
										displayErrorMessage(ERROR_DUPLICATE_IDENTIFIER_NAME, parameterName, cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea());
									} else if (result.getFailureReason() == SaveUpdateCQLResult.NODE_NOT_FOUND) {
										displayErrorMessage(UNABLE_TO_FIND_NODE_TO_MODIFY, parameterName, cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea());
									} else if (result.getFailureReason() == SaveUpdateCQLResult.NO_SPECIAL_CHAR) {
										displayErrorMessage(ERROR_PARAMETER_NAME_NO_SPECIAL_CHAR, parameterName, cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea());
									} else if (result.getFailureReason() == SaveUpdateCQLResult.COMMENT_INVALID) {
										messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getERROR_VALIDATION_COMMENT_AREA());
									}
								}
								showSearchingBusy(false);
							}
						});
			}
		} else {
			cqlWorkspaceView.getCQLParametersView().getParamNameGroup().setValidationState(ValidationState.ERROR);
			messagePanel.getErrorMessageAlert().createAlert(parameterName.isEmpty() 
					? ERROR_SAVE_CQL_PARAMETER
							: "Invalid Parameter name. " + DEFINED_KEYWORD_EXPRESION_ERROR_MESSAGE);
			cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea().setText(parameterName.trim());
		}
	}
	
	@Override
	protected void saveCQLFile() {
		
		if(hasEditPermissions()) {
			
			String currentCQL = cqlWorkspaceView.getCQLLibraryEditorView().getCqlAceEditor().getText();		
			MatContext.get().getLibraryService().saveCQLFile(MatContext.get().getCurrentCQLLibraryId(), currentCQL, new AsyncCallback<SaveUpdateCQLResult>() {
	
				@Override
				public void onFailure(Throwable caught) {
				}
				@Override
				public void onSuccess(SaveUpdateCQLResult result) {
					cqlWorkspaceView.getCQLLibraryEditorView().getCqlAceEditor().replace(result.getCqlString());
					messagePanel.clearAlerts();
					if(!result.isSuccess()) {
						onSaveCQLFileFailure(result);
					} else {
						handleCQLData(result);
						onSaveCQLFileSuccess(result);
						setIsPageDirty(false);
					}
					cqlWorkspaceView.getCQLLibraryEditorView().getCqlAceEditor().focus();
				}
			});
		}
	}

	@Override
	protected void addAndModifyDefintions() {
		cqlWorkspaceView.resetMessageDisplay();
		final String definitionName = cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea().getText();
		String definitionLogic = cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().getText();
		String definitionComment = cqlWorkspaceView.getCQLDefinitionsView().getDefineCommentTextArea().getText();
		String defineContext = EMPTY_STRING;

		boolean isValidDefinitionName = isValidExpressionName(definitionName);
		if (isValidDefinitionName) {
			if (validator.hasSpecialCharacter(definitionName.trim())) {
				cqlWorkspaceView.getCQLDefinitionsView().getDefineNameGroup().setValidationState(ValidationState.ERROR);
				messagePanel.getErrorMessageAlert().createAlert(ERROR_DEFINITION_NAME_NO_SPECIAL_CHAR);
				cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea().setText(definitionName.trim());
			} else if(validator.isCommentMoreThan250Characters(definitionComment)){
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getERROR_VALIDATION_COMMENT_AREA());
				cqlWorkspaceView.getCQLDefinitionsView().getDefineCommentGroup().setValidationState(ValidationState.ERROR);
			} else if(validator.doesCommentContainInvalidCharacters(definitionComment)){
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getINVALID_COMMENT_CHARACTERS());
				cqlWorkspaceView.getCQLDefinitionsView().getDefineCommentGroup().setValidationState(ValidationState.ERROR);
			} else {
				final CQLDefinition define = new CQLDefinition();
				define.setName(definitionName);
				define.setLogic(definitionLogic);
				define.setCommentString(definitionComment);
				CQLDefinition toBeModifiedObj = null;

				if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedDefinitionObjId() != null) {
					toBeModifiedObj = cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefinitionMap()
							.get(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedDefinitionObjId());
				}
				showSearchingBusy(true);
				MatContext.get().getCQLLibraryService().saveAndModifyDefinitions(
						MatContext.get().getCurrentCQLLibraryId(), toBeModifiedObj, define,
						cqlWorkspaceView.getCQLLeftNavBarPanelView().getViewDefinitions(),
						isFormattable, new AsyncCallback<SaveUpdateCQLResult>() {

							@Override
							public void onFailure(Throwable caught) {
								cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedDefinitionObjId(null);
								messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
								showSearchingBusy(false);
							}

							@Override
							public void onSuccess(SaveUpdateCQLResult result) {
								if (result != null) {
									if (result.isSuccess()) {
										cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewDefinitions(result.getCqlModel().getDefinitionList());
										MatContext.get().setDefinitions(getDefinitionList(result.getCqlModel().getDefinitionList()));
										MatContext.get().setExpressionToReturnTypeMap(result.getUsedCQLArtifacts().getExpressionToReturnTypeMap());
										cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedDefinitionObjId(result.getDefinition().getId());
										cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddDefinitionNamesToListBox();
										cqlWorkspaceView.getCQLLeftNavBarPanelView().updateDefineMap();
										messagePanel.getErrorMessageAlert().clearAlert();
										cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea().setText(result.getDefinition().getName());
										cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().replace(result.getDefinition().getLogic());
										setIsPageDirty(false);
										cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().clearAnnotations();
										cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().removeAllMarkers();
										cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setText(result.getDefinition().getReturnType());
										if (SharedCQLWorkspaceUtility.validateCQLArtifact(result, cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor(), messagePanel, DEFINITION, definitionName.trim())) {
											cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
											cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression");
										} else if (!result.isDatatypeUsedCorrectly()) {
											if(result.isValidCQLWhileSavingExpression()){
												cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setText(result.getDefinition().getReturnType());
												cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression is "+result.getDefinition().getReturnType());
											} else {
												cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
												cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression");
											}
										} else {
											if(result.isValidCQLWhileSavingExpression()){
												cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setText(result.getDefinition().getReturnType());
												cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression is "+result.getDefinition().getReturnType());
											} else {
												cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
												cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression");
											}
										}
										cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().setAnnotations();
										cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().redisplay();
									} else {
										if (result.getFailureReason() == SaveUpdateCQLResult.NAME_NOT_UNIQUE) {
											displayErrorMessage(ERROR_DUPLICATE_IDENTIFIER_NAME, definitionName, cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea());
										} else if (result.getFailureReason() == SaveUpdateCQLResult.NODE_NOT_FOUND) {
											displayErrorMessage(UNABLE_TO_FIND_NODE_TO_MODIFY, definitionName, cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea());
										} else if (result.getFailureReason() == SaveUpdateCQLResult.NO_SPECIAL_CHAR) {
											displayErrorMessage(ERROR_DEFINITION_NAME_NO_SPECIAL_CHAR, definitionName, cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea());
										} else if (result.getFailureReason() == SaveUpdateCQLResult.COMMENT_INVALID) {
											messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getERROR_VALIDATION_COMMENT_AREA());
										}
									}
								}
								
								showSearchingBusy(false);
							}
						});
			}
		} else {
			cqlWorkspaceView.getCQLDefinitionsView().getDefineNameGroup().setValidationState(ValidationState.ERROR);
			messagePanel.getErrorMessageAlert().createAlert(definitionName.isEmpty() ? ERROR_SAVE_CQL_DEFINITION : "Invalid Definition name. " + DEFINED_KEYWORD_EXPRESION_ERROR_MESSAGE);
			cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea().setText(definitionName.trim());
		}
	}

	@Override
	protected void addIncludeLibraryInCQLLookUp() {
		cqlWorkspaceView.resetMessageDisplay();
		if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox().getItemCount() >= CQLWorkSpaceConstants.VALID_INCLUDE_COUNT) {
			messagePanel.getWarningMessageAlert().createAlert(MatContext.get().getMessageDelegate().getCqlLimitWarningMessage());
			return;
		} else {
			messagePanel.getWarningMessageAlert().clearAlert();
		}
		final String aliasName = cqlWorkspaceView.getIncludeView().getAliasNameTxtArea().getText();

		if (!aliasName.isEmpty() && cqlWorkspaceView.getIncludeView().getSelectedObjectList().size() > 0) {
			CQLLibraryDataSetObject cqlLibraryDataSetObject = cqlWorkspaceView.getIncludeView().getSelectedObjectList().get(0);
			if (validator.doesAliasNameFollowCQLAliasNamingConvention(aliasName.trim())) {
				CQLIncludeLibrary incLibrary = new CQLIncludeLibrary();
				incLibrary.setAliasName(aliasName);
				incLibrary.setCqlLibraryId(cqlLibraryDataSetObject.getId());
				String versionValue = cqlLibraryDataSetObject.getVersion().replace("v", EMPTY_STRING) + "." + "000";
				incLibrary.setVersion(versionValue);
				incLibrary.setCqlLibraryName(cqlLibraryDataSetObject.getCqlName());
				incLibrary.setQdmVersion(cqlLibraryDataSetObject.getQdmVersion());
				incLibrary.setSetId(cqlLibraryDataSetObject.getCqlSetId());
				if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedIncLibraryObjId() == null) {
					// this is just to add include library and not modify
					MatContext.get().getCQLLibraryService().saveIncludeLibrayInCQLLookUp(
							MatContext.get().getCurrentCQLLibraryId(), null, incLibrary,
							cqlWorkspaceView.getCQLLeftNavBarPanelView().getViewIncludeLibrarys(),
							new AsyncCallback<SaveUpdateCQLResult>() {

								@Override
								public void onFailure(Throwable caught) {
									showSearchingBusy(false);
									if(caught instanceof InvalidLibraryException) {
										messagePanel.getErrorMessageAlert().createAlert(caught.getMessage());
									} else {
										messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
									}
								}

								@Override
								public void onSuccess(SaveUpdateCQLResult result) {
									if (result != null) {
										if (result.isSuccess()) {
											cqlWorkspaceView.resetMessageDisplay();
											setIsPageDirty(false);
											cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewIncludeLibrarys(result.getCqlModel().getCqlIncludeLibrarys());
											MatContext.get().setIncludes(getIncludesList(result.getCqlModel().getCqlIncludeLibrarys()));
											MatContext.get().setCQLModel(result.getCqlModel());
											cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddAliasNamesToListBox();
											cqlWorkspaceView.getCQLLeftNavBarPanelView().udpateIncludeLibraryMap();
											cqlWorkspaceView.getIncludeView().setIncludedList(cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludedList(cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludeLibraryMap()));
											messagePanel.getSuccessMessageAlert().createAlert(getIncludeLibrarySuccessMessage(result.getIncludeLibrary().getAliasName()));
											clearAlias();
											MatContext.get().setIncludedValues(result);
											if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox().getItemCount() >= CQLWorkSpaceConstants.VALID_INCLUDE_COUNT) {
												messagePanel.getWarningMessageAlert().createAlert(MatContext.get().getMessageDelegate().getCqlLimitWarningMessage());
											} else {
												messagePanel.getWarningMessageAlert().clearAlert();
											}
										}
									}
								}
							});
				}
			} else {
				((CQLStandaloneWorkSpaceView)cqlWorkspaceView).getCqlIncludeLibraryView().getAliasNameGroup().setValidationState(ValidationState.ERROR);
				messagePanel.getErrorMessageAlert().createAlert(ERROR_INCLUDE_ALIAS_NAME_NO_SPECIAL_CHAR);
				((CQLStandaloneWorkSpaceView)cqlWorkspaceView).getAliasNameTxtArea().setText(aliasName.trim());
			}
		} else {
			((CQLStandaloneWorkSpaceView)cqlWorkspaceView).getCqlIncludeLibraryView().getAliasNameGroup().setValidationState(ValidationState.ERROR);
			messagePanel.getErrorMessageAlert().createAlert(SAVE_INCLUDE_LIBRARY_VALIATION_ERROR);
		}
	}

	@Override
	protected void deleteDefinition() {
		cqlWorkspaceView.resetMessageDisplay();
		final String definitionName = cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea().getText();
		if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedDefinitionObjId() != null) {
			final CQLDefinition toBeModifiedObj = cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefinitionMap().get(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedDefinitionObjId());
			showSearchingBusy(true);
			MatContext.get().getCQLLibraryService().deleteDefinition(MatContext.get().getCurrentCQLLibraryId(), toBeModifiedObj, new AsyncCallback<SaveUpdateCQLResult>() {

				@Override
				public void onFailure(Throwable caught) {
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedDefinitionObjId(null);
					messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
					showSearchingBusy(false);
				}

				@Override
				public void onSuccess(SaveUpdateCQLResult result) {
					if (result != null) {
						if (result.isSuccess()) {
							cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewDefinitions(result.getCqlModel().getDefinitionList());
							MatContext.get().setDefinitions(getDefinitionList(result.getCqlModel().getDefinitionList()));
							MatContext.get().setExpressionToReturnTypeMap(result.getUsedCQLArtifacts().getExpressionToReturnTypeMap());
							cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddDefinitionNamesToListBox();
							cqlWorkspaceView.getCQLLeftNavBarPanelView().updateDefineMap();
							messagePanel.getErrorMessageAlert().clearAlert();
							messagePanel.getSuccessMessageAlert().setVisible(true);
							cqlWorkspaceView.getCQLLeftNavBarPanelView().getSearchSuggestDefineTextBox().setText(EMPTY_STRING);
							cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea().setText(EMPTY_STRING);
							cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().setText(EMPTY_STRING);
							cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedDefinitionObjId(null);
							setIsPageDirty(false);
							cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().clearAnnotations();
							cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().removeAllMarkers();
							cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().setAnnotations();
							cqlWorkspaceView.getCQLDefinitionsView().getDefineButtonBar().getDeleteButton().setEnabled(false);
							messagePanel.getSuccessMessageAlert().createAlert(buildRemovedSuccessfullyMessage(DEFINITION, toBeModifiedObj.getName()));
							cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
						} else if (result.getFailureReason() == SaveUpdateCQLResult.NODE_NOT_FOUND) {
							displayErrorMessage(UNABLE_TO_FIND_NODE_TO_MODIFY, definitionName, cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea());
						} else if (result.getFailureReason() == SaveUpdateCQLResult.SERVER_SIDE_VALIDATION) {
							displayErrorMessage(UNAUTHORIZED_DELETE_OPERATION, definitionName, cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea());
						}
					}
					showSearchingBusy(false);
					cqlWorkspaceView.getCQLDefinitionsView().getMainDefineViewVerticalPanel().setFocus(true);
				}
			});
		} else {
			cqlWorkspaceView.resetMessageDisplay();
			messagePanel.getErrorMessageAlert().createAlert(SELECT_DEFINITION_TO_DELETE);
			cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea().setText(definitionName.trim());
		}
	}

	protected void deleteFunction() {
		cqlWorkspaceView.resetMessageDisplay();
		final String functionName = cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().getText();
		if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedFunctionObjId() != null) {
			final CQLFunctions toBeModifiedFuncObj = cqlWorkspaceView.getCQLLeftNavBarPanelView().getFunctionMap().get(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedFunctionObjId());
			showSearchingBusy(true);
			MatContext.get().getCQLLibraryService().deleteFunction(MatContext.get().getCurrentCQLLibraryId(),
					toBeModifiedFuncObj,
					new AsyncCallback<SaveUpdateCQLResult>() {

				@Override
				public void onFailure(Throwable caught) {
					messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
					showSearchingBusy(false);
				}

				@Override
				public void onSuccess(SaveUpdateCQLResult result) {
					if (result != null) {
						if (result.isSuccess()) {
							cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewFunctions(result.getCqlModel().getCqlFunctions());
							MatContext.get().setFuncs(getFunctionList(result.getCqlModel().getCqlFunctions()));
							MatContext.get().setExpressionToReturnTypeMap(result.getUsedCQLArtifacts().getExpressionToReturnTypeMap());
							cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddFunctionsNamesToListBox();
							cqlWorkspaceView.getCQLLeftNavBarPanelView().updateFunctionMap();
							messagePanel.getErrorMessageAlert().clearAlert();
							cqlWorkspaceView.getCQLFunctionsView().getFunctionArgNameMap().clear();
							cqlWorkspaceView.getCQLFunctionsView().getFunctionArgumentList().clear();
							cqlWorkspaceView.getCQLLeftNavBarPanelView().getSearchSuggestFuncTextBox().setText(EMPTY_STRING);
							messagePanel.getSuccessMessageAlert().setVisible(true);
							cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().setText(EMPTY_STRING);
							cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().setText(EMPTY_STRING);
							cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionObjId(null);
							cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionArgumentObjId(null);
							cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionArgumentName(null);
							setIsPageDirty(false);
							cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().clearAnnotations();
							cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().removeAllMarkers();
							cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().setAnnotations();
							cqlWorkspaceView.getCQLFunctionsView().getFunctionButtonBar().getDeleteButton().setEnabled(false);
							messagePanel.getSuccessMessageAlert().createAlert(buildRemovedSuccessfullyMessage(FUNCTION, toBeModifiedFuncObj.getName()));
							cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
							if (result.getFunction() != null) {
								((CQLStandaloneWorkSpaceView)cqlWorkspaceView).createAddArgumentViewForFunctions(
										new ArrayList<CQLFunctionArgument>());
							}
						} else if (result.getFailureReason() == SaveUpdateCQLResult.NODE_NOT_FOUND) {
							displayErrorMessage(UNABLE_TO_FIND_NODE_TO_MODIFY, functionName, cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea());
						} else if (result.getFailureReason() == SaveUpdateCQLResult.SERVER_SIDE_VALIDATION) {
							displayErrorMessage(UNAUTHORIZED_DELETE_OPERATION, functionName, cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea());
						}
					}
					showSearchingBusy(false);
					cqlWorkspaceView.getCQLFunctionsView().getMainFunctionVerticalPanel().setFocus(true);
				}
			});
		} else {
			cqlWorkspaceView.resetMessageDisplay();
			messagePanel.getErrorMessageAlert().createAlert(SELECT_FUNCTION_TO_DELETE);
			cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().setText(functionName.trim());
		}
	}

	protected void deleteFunctionArgument(){
		String funcArgName = null;
		cqlWorkspaceView.resetMessageDisplay();
		setIsPageDirty(true);
		Iterator<CQLFunctionArgument> iterator = cqlWorkspaceView.getCQLFunctionsView().getFunctionArgumentList().iterator();
		cqlWorkspaceView.getCQLFunctionsView().getFunctionArgNameMap().remove(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedFunctionArgumentName().toLowerCase());
		while (iterator.hasNext()) {
			CQLFunctionArgument cqlFunArgument = iterator.next();
			if (cqlFunArgument.getId().equals(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedFunctionArgumentObjId())) {
				iterator.remove();
				cqlWorkspaceView.getCQLFunctionsView().createAddArgumentViewForFunctions(cqlWorkspaceView.getCQLFunctionsView().getFunctionArgumentList(), hasEditPermissions());
				funcArgName = cqlFunArgument.getArgumentName();
				break;
			}
		}

		cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionArgumentName(null);
		cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionArgumentObjId(null);
		cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().setFocus(true);
		messagePanel.getSuccessMessageAlert().createAlert(buildRemovedSuccessfullyMessage(ARGUMENT, funcArgName));
		setIsPageDirty(true);
	}


	protected void deleteParameter() {
		cqlWorkspaceView.resetMessageDisplay();
		final String parameterName = cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea().getText();
		if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedParamerterObjId() != null) {
			final CQLParameter toBeModifiedParamObj = cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterMap().get(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedParamerterObjId());
			showSearchingBusy(true);
			MatContext.get().getCQLLibraryService().deleteParameter(MatContext.get().getCurrentCQLLibraryId(), toBeModifiedParamObj,
					new AsyncCallback<SaveUpdateCQLResult>() {

				@Override
				public void onFailure(Throwable caught) {
					messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
					showSearchingBusy(false);
				}

				@Override
				public void onSuccess(SaveUpdateCQLResult result) {
					if (result != null) {
						if (result.isSuccess()) {
							cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewParameterList((result.getCqlModel().getCqlParameters()));
							MatContext.get().setParameters(getParameterList(result.getCqlModel().getCqlParameters()));
							MatContext.get().setExpressionToReturnTypeMap(result.getUsedCQLArtifacts().getExpressionToReturnTypeMap());
							cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddParameterNamesToListBox();
							cqlWorkspaceView.getCQLLeftNavBarPanelView().updateParamMap();
							messagePanel.getErrorMessageAlert().clearAlert();
							messagePanel.getSuccessMessageAlert().setVisible(true);
							cqlWorkspaceView.getCQLLeftNavBarPanelView().getSearchSuggestParamTextBox().setText(EMPTY_STRING);
							cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea().setText(EMPTY_STRING);
							cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().setText(EMPTY_STRING);
							cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedParamerterObjId(null);
							setIsPageDirty(false);
							cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().clearAnnotations();
							cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().removeAllMarkers();
							cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().setAnnotations();
							cqlWorkspaceView.getCQLParametersView().getParameterButtonBar().getDeleteButton().setEnabled(false);
							messagePanel.getSuccessMessageAlert().createAlert(buildRemovedSuccessfullyMessage(PARAMETER, toBeModifiedParamObj.getName()));  
						} else if (result.getFailureReason() == SaveUpdateCQLResult.NODE_NOT_FOUND) {
							displayErrorMessage(UNABLE_TO_FIND_NODE_TO_MODIFY, parameterName, cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea());
						}else if (result.getFailureReason() == SaveUpdateCQLResult.SERVER_SIDE_VALIDATION) {
							displayErrorMessage(UNAUTHORIZED_DELETE_OPERATION, parameterName, cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea());
						}
					}
					showSearchingBusy(false);
					cqlWorkspaceView.getCQLParametersView().getMainParamViewVerticalPanel().setFocus(true);
				}
			});

		}else {
			cqlWorkspaceView.resetMessageDisplay();
			messagePanel.getErrorMessageAlert().createAlert(SELECT_PARAMETER_TO_DELETE);
			cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea().setText(parameterName.trim());
		}
	}

	protected void deleteInclude() {
		cqlWorkspaceView.resetMessageDisplay();
		final String aliasName = cqlWorkspaceView.getIncludeView().getAliasNameTxtArea().getText();
		if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedIncLibraryObjId() != null) {
			final CQLIncludeLibrary toBeModifiedIncludeObj = cqlWorkspaceView.getCQLLeftNavBarPanelView()
					.getIncludeLibraryMap()
					.get(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedIncLibraryObjId());
			showSearchingBusy(true);
			MatContext.get().getCQLLibraryService().deleteInclude(MatContext.get().getCurrentCQLLibraryId(), toBeModifiedIncludeObj, 
				new AsyncCallback<SaveUpdateCQLResult>() {

				@Override
				public void onFailure(Throwable caught) {
					messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
					showSearchingBusy(false);
				}

				@Override
				public void onSuccess(SaveUpdateCQLResult result) {
					if (result.isSuccess()) {
						cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewIncludeLibrarys(result.getCqlModel().getCqlIncludeLibrarys());
						MatContext.get().setIncludes(getIncludesList(result.getCqlModel().getCqlIncludeLibrarys()));
						MatContext.get().setIncludedValues(result);
						cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddAliasNamesToListBox();
						cqlWorkspaceView.getCQLLeftNavBarPanelView().udpateIncludeLibraryMap();
						messagePanel.getErrorMessageAlert().clearAlert();
						messagePanel.getSuccessMessageAlert().setVisible(true);
						cqlWorkspaceView.getCQLLeftNavBarPanelView().getSearchSuggestIncludeTextBox().setText(EMPTY_STRING);
						cqlWorkspaceView.getIncludeView().getAliasNameTxtArea().setText(EMPTY_STRING);
						cqlWorkspaceView.getIncludeView().getCqlLibraryNameTextBox().setText(EMPTY_STRING);
						cqlWorkspaceView.getIncludeView().getOwnerNameTextBox().setText(EMPTY_STRING);
						cqlWorkspaceView.getIncludeView().getViewCQLEditor().setText(EMPTY_STRING);
						cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedIncLibraryObjId(null);
						setIsPageDirty(false);
						cqlWorkspaceView.getIncludeView().getViewCQLEditor().clearAnnotations();
						cqlWorkspaceView.getIncludeView().getViewCQLEditor().removeAllMarkers();
						cqlWorkspaceView.getIncludeView().getViewCQLEditor().setAnnotations();
						cqlWorkspaceView.getIncludeView().getDeleteButton().setEnabled(false);

						cqlWorkspaceView.getIncludeView().getCloseButton().fireEvent(new GwtEvent<ClickHandler>() {
							@Override
							public com.google.gwt.event.shared.GwtEvent.Type<ClickHandler> getAssociatedType() {
								return ClickEvent.getType();
							}

							@Override
							protected void dispatch(ClickHandler handler) {
								handler.onClick(null);
							}
						});
						messagePanel.getSuccessMessageAlert().createAlert(buildRemovedSuccessfullyMessage(LIBRARY, toBeModifiedIncludeObj.getAliasName()));
					} else if (result.getFailureReason() == SaveUpdateCQLResult.NODE_NOT_FOUND) {
						displayErrorMessage(UNABLE_TO_FIND_NODE_TO_MODIFY, aliasName, cqlWorkspaceView.getIncludeView().getAliasNameTxtArea());
					}
					showSearchingBusy(false);
				}
			});
		} else {
			cqlWorkspaceView.resetMessageDisplay();
			messagePanel.getErrorMessageAlert().createAlert(SELECT_ALIAS_TO_DELETE);
			cqlWorkspaceView.getIncludeView().getAliasNameTxtArea().setText(aliasName.trim());
		}
	}

	@Override
	protected void deleteCode(){
		cqlWorkspaceView.resetMessageDisplay();
		showSearchingBusy(true);
		MatContext.get().getCQLLibraryService().deleteCode(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedCodesObjId(), MatContext.get().getCurrentCQLLibraryId(), new AsyncCallback<SaveUpdateCQLResult>() {

			@Override
			public void onFailure(Throwable caught) {
				cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedCodesObjId(null);
				showSearchingBusy(false);
				Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
			}

			@Override
			public void onSuccess(SaveUpdateCQLResult result) {
				cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedCodesObjId(null);
				showSearchingBusy(false);
				if(result.isSuccess()){
					messagePanel.getSuccessMessageAlert().createAlert(buildRemovedSuccessfullyMessage(CODE, result.getCqlCode().getCodeOID()));
					cqlWorkspaceView.getCodesView().resetCQLCodesSearchPanel();
					appliedCodeTableList.clear();
					List<CQLCode> codesToView = result.getCqlModel().getCodeList();
					codesToView = codesToView.stream().filter(c -> c.getCodeIdentifier() != null && !c.getCodeIdentifier().isEmpty()).collect(Collectors.toList());
					appliedCodeTableList.addAll(codesToView);
					MatContext.get().getCQLModel().setCodeList(result.getCqlModel().getCodeList());
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setCodeBadgeValue(appliedCodeTableList);
					cqlWorkspaceView.getCodesView().buildCodesCellTable(appliedCodeTableList, hasEditPermissions());
					getAppliedValuesetAndCodeList();
				} else {
					messagePanel.getErrorMessageAlert().createAlert("Unable to delete.");		
				}

				cqlWorkspaceView.getCodesView().getCodeSearchInput().setFocus(true);
			}
		});
	}

	@Override
	protected void checkAndDeleteValueSet(){
		MatContext.get().getLibraryService().getCQLData(MatContext.get().getCurrentCQLLibraryId(), new AsyncCallback<SaveUpdateCQLResult>() {

			@Override
			public void onSuccess(final SaveUpdateCQLResult result) {
				cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedValueSetObjId(null);
				appliedValueSetTableList.clear();
				if (result.getCqlModel().getAllValueSetAndCodeList() != null) {
					for (CQLQualityDataSetDTO dto : result.getCqlModel().getAllValueSetAndCodeList()) {
						if(dto.getOid().equals("419099009") || dto.getOid().equals("21112-8") 
								|| (dto.getType() !=null && dto.getType().equalsIgnoreCase("code")))
							continue;
						appliedValueSetTableList.add(dto);
					}

					if (appliedValueSetTableList.size() > 0) {
						Iterator<CQLQualityDataSetDTO> iterator = appliedValueSetTableList
								.iterator();
						while (iterator.hasNext()) {
							CQLQualityDataSetDTO dataSetDTO = iterator.next();
							if (dataSetDTO.getUuid() != null) {
								if (dataSetDTO.getUuid().equals(cqlWorkspaceView.getValueSetView().getSelectedElementToRemove().getUuid())) {
									deleteValueSet(dataSetDTO.getId());
								}
							}
						}
					}
				}
				showSearchingBusy(false);
			}

			@Override
			public void onFailure(Throwable caught) {
				cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedValueSetObjId(null);
				showSearchingBusy(false);
				Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
			}
		});
	}

	private void saveCQLGeneralInformation(String libraryName, String libraryComment) {
		String libraryId = MatContext.get().getCurrentCQLLibraryId();
		showSearchingBusy(true);
		MatContext.get().getCQLLibraryService().saveAndModifyCQLGeneralInfo(libraryId, libraryName, libraryComment, new AsyncCallback<SaveUpdateCQLResult>() {

			@Override
			public void onFailure(Throwable caught) {
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
				showSearchingBusy(false);
			}

			@Override
			public void onSuccess(SaveUpdateCQLResult result) {
				if (result != null) {
					if (result.isSuccess()) {
						cqlLibraryName = result.getCqlModel().getLibraryName().trim();
						cqlLibraryComment = result.getCqlModel().getLibraryComment();
						cqlWorkspaceView.getCqlGeneralInformationView().getLibraryNameTextBox().setText(cqlLibraryName);
						cqlWorkspaceView.getCqlGeneralInformationView().getCommentsTextBox().setText(result.getCqlModel().getLibraryComment());
						cqlWorkspaceView.getCqlGeneralInformationView().getCommentsTextBox().setCursorPos(0);
						displayMsgAndResetDirtyPostSave(cqlLibraryName);
						MatContext.get().getCurrentLibraryInfo().setLibraryName(cqlLibraryName);
						CqlComposerPresenter.setContentHeading();
					} else {
						if (result.getFailureReason() == SaveUpdateCQLResult.DUPLICATE_LIBRARY_NAME) {
							isLibraryNameExists = true;
							messagePanel.getErrorMessageAlert().createAlert(MessageDelegate.DUPLICATE_LIBRARY_NAME_SAVE);
						} else {
							messagePanel.getErrorMessageAlert().createAlert(MessageDelegate.GENERIC_ERROR_MESSAGE);
						}
					}
				}
				showSearchingBusy(false);
			}
		});
	}

	private void logRecentActivity() {
		MatContext.get().getCQLLibraryService().isLibraryAvailableAndLogRecentActivity(MatContext.get().getCurrentCQLLibraryId(), MatContext.get().getLoggedinUserId(),
				new AsyncCallback<Void>() {

					@Override
					public void onFailure(Throwable caught) {}

					@Override
					public void onSuccess(Void result) {
						isCQLWorkSpaceLoaded = true;
						displayCQLView();
					}
				});
	}

	private void displayCQLView() {
		panel.clear();
		currentSection = CQLWorkSpaceConstants.CQL_GENERAL_MENU;
		cqlWorkspaceView.buildView(messagePanel, buildHelpBlock(), hasEditPermissions());
		addLeftNavEventHandler();
		getCQLDataForLoad(); 
		cqlWorkspaceView.resetMessageDisplay();
		panel.add(cqlWorkspaceView.asWidget());
	}


	@Override
	public void beforeClosingDisplay() {
		currentIncludeLibrarySetId = null;
		currentIncludeLibraryId = null; 
		cqlWorkspaceView.getCQLLeftNavBarPanelView().clearShotcutKeyList();
		cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedDefinitionObjId(null);
		cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedParamerterObjId(null);
		cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionObjId(null);
		cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionArgumentObjId(null);
		cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionArgumentName(null);
		cqlWorkspaceView.getCQLFunctionsView().getFunctionArgNameMap().clear();
		cqlWorkspaceView.getValueSetView().clearCellTableMainPanel();
		cqlWorkspaceView.getCodesView().clearCellTableMainPanel();
		cqlWorkspaceView.getIncludeView().getSearchTextBox().setText(EMPTY_STRING);
		setIsPageDirty(false);
		cqlWorkspaceView.resetMessageDisplay();
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesCollapse().getElement().setClassName(PANEL_COLLAPSE_COLLAPSE);
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getParamCollapse().getElement().setClassName(PANEL_COLLAPSE_COLLAPSE);
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefineCollapse().getElement().setClassName(PANEL_COLLAPSE_COLLAPSE);
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getFunctionCollapse().getElement().setClassName(PANEL_COLLAPSE_COLLAPSE);
		if (cqlWorkspaceView.getCQLFunctionsView().getFunctionArgumentList().size() > 0) {
			cqlWorkspaceView.getCQLFunctionsView().getFunctionArgumentList().clear();
		}
		isModified = false;
		isCodeModified = false;
		setId = null;
		modifyValueSetDTO = null;
		curAceEditor = null;
		currentSection = CQLWorkSpaceConstants.CQL_GENERAL_MENU;
		messagePanel.clearAlerts();
		helpBlock.clearError();
		cqlWorkspaceView.resetAll();
		setIsPageDirty(false);
		panel.clear();
		cqlWorkspaceView.getMainPanel().clear();
		MatContext.get().getValuesets().clear();
	}

	@Override
	public void beforeDisplay() {
		if ((MatContext.get().getCurrentCQLLibraryId() == null) || MatContext.get().getCurrentCQLLibraryId().isEmpty()) {
			displayEmpty();
		} else {
			panel.clear();
			((CQLStandaloneWorkSpaceView)cqlWorkspaceView).getLockedButtonVPanel();
			panel.add(cqlWorkspaceView.asWidget());
			if (!isCQLWorkSpaceLoaded) { 
				// this check is made so that when CQL is clicked from CQL library, its not called twice.
				displayCQLView();
			} else {
				isCQLWorkSpaceLoaded = false;
			}
		}
		CqlComposerPresenter.setSubSkipEmbeddedLink("CQLStandaloneWorkSpaceView.containerPanel");
		focusSkipLists();
	}

	private void getCQLDataForLoad() {
		showSearchingBusy(true);
		MatContext.get().getCQLLibraryService().getCQLDataForLoad(MatContext.get().getCurrentCQLLibraryId(), new AsyncCallback<SaveUpdateCQLResult>() {

			@Override
			public void onSuccess(SaveUpdateCQLResult result) {
				handleCQLData(result);
				showSearchingBusy(false);
			}

			@Override
			public void onFailure(Throwable caught) {
				showSearchingBusy(false);
			}
		});
	}


	private void handleCQLData(SaveUpdateCQLResult result) {
		if (result.isSuccess()) {
			if (result.getCqlModel() != null) {
				if (result.getSetId() != null) {
					setId = result.getSetId();
				}
				if (result.getCqlModel().getLibraryName() != null) {
					isLibraryNameExists = (result.getFailureReason() == SaveUpdateCQLResult.DUPLICATE_LIBRARY_NAME);
					cqlLibraryName = cqlWorkspaceView.getCqlGeneralInformationView().createCQLLibraryName(MatContext.get().getCurrentCQLLibraryeName());
					cqlLibraryComment = result.getCqlModel().getLibraryComment();
					String libraryVersion = MatContext.get().getCurrentCQLLibraryVersion();
					libraryVersion = libraryVersion.replaceAll("Draft ", EMPTY_STRING).trim();
					if (libraryVersion.startsWith("v")) {
						libraryVersion = libraryVersion.substring(1);
					}
					cqlWorkspaceView.getCqlGeneralInformationView().setGeneralInfoOfLibrary(cqlLibraryName, libraryVersion, result.getCqlModel().getQdmVersion(), "QDM", cqlLibraryComment);
				}

				List<CQLQualityDataSetDTO> appliedValueSetAndCodeList =  result.getCqlModel().getAllValueSetAndCodeList();				
				MatContext.get().setValuesets(appliedValueSetAndCodeList);
				MatContext.get().setCQLModel(result.getCqlModel());
				appliedValueSetTableList.clear();
				appliedCodeTableList.clear();
				
				for (CQLQualityDataSetDTO dto : result.getCqlModel().getValueSetList()) {
					if (dto.getOriginalCodeListName() == null || dto.getOriginalCodeListName().isEmpty()) {
						continue;
					}
					
					appliedValueSetTableList.add(dto);
				}
				
				cqlWorkspaceView.getCQLLeftNavBarPanelView().setAppliedQdmTableList(appliedValueSetTableList);
				cqlWorkspaceView.getCQLLeftNavBarPanelView().updateValueSetMap(appliedValueSetTableList);

				if (result.getCqlModel().getCodeList() != null) {
					List<CQLCode> codesToView = result.getCqlModel().getCodeList();
					codesToView = codesToView.stream().filter(c -> c.getCodeIdentifier() != null && !c.getCodeIdentifier().isEmpty()).collect(Collectors.toList());
					appliedCodeTableList.addAll(codesToView);
				}
				
				cqlWorkspaceView.getCQLLeftNavBarPanelView().setCodeBadgeValue(appliedCodeTableList);
				cqlWorkspaceView.getCQLLeftNavBarPanelView().setAppliedCodeTableList(appliedCodeTableList);
				MatContext.get().getCQLModel().setCodeList(appliedCodeTableList);

				if ((result.getCqlModel().getDefinitionList() != null) && (result.getCqlModel().getDefinitionList().size() > 0)) {
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewDefinitions(result.getCqlModel().getDefinitionList());
					cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddDefinitionNamesToListBox();
					cqlWorkspaceView.getCQLLeftNavBarPanelView().updateDefineMap();
					MatContext.get().setDefinitions(getDefinitionList(result.getCqlModel().getDefinitionList()));
				} 
				
				if (result.getCqlModel().getCqlParameters() != null) {
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewParameterList(result.getCqlModel().getCqlParameters());
					cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddParameterNamesToListBox();
					cqlWorkspaceView.getCQLLeftNavBarPanelView().updateParamMap();
					MatContext.get().setParameters(getParameterList(result.getCqlModel().getCqlParameters()));
				} 
				
				if (result.getCqlModel().getCqlFunctions() != null) {
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewFunctions(result.getCqlModel().getCqlFunctions());
					cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddFunctionsNamesToListBox();
					cqlWorkspaceView.getCQLLeftNavBarPanelView().updateFunctionMap();
					MatContext.get().setFuncs(getFunctionList(result.getCqlModel().getCqlFunctions()));
				}
				
				MatContext.get().setExpressionToReturnTypeMap(result.getUsedCQLArtifacts().getExpressionToReturnTypeMap());
				
				List<CQLIncludeLibrary> includedLibrariesForViewing = result.getCqlModel().getCqlIncludeLibrarys();
				if (includedLibrariesForViewing != null) {
					includedLibrariesForViewing.removeIf(l -> l.getCqlLibraryId() == null || l.getCqlLibraryId().isEmpty());
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewIncludeLibrarys(result.getCqlModel().getCqlIncludeLibrarys());
					cqlWorkspaceView.getCQLLeftNavBarPanelView().clearAndAddAliasNamesToListBox();
					cqlWorkspaceView.getCQLLeftNavBarPanelView().udpateIncludeLibraryMap();
					MatContext.get().setIncludes(getIncludesList(result.getCqlModel().getCqlIncludeLibrarys()));
					MatContext.get().setIncludedValues(result);
				}
				
				buildOrClearErrorPanel();
			}
		}
	}

	private void addLeftNavEventHandler() {
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getGeneralInformation().addClickHandler(event -> checkIfLibraryNameExistsAndLoadGeneralInfo());
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesLibrary().addClickHandler(event -> leftNavIncludesLibraryClicked(event));
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getCodesLibrary().addClickHandler(event -> leftNavBarCodesClicked(event));
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getAppliedQDM().addClickHandler(event -> leftNavAppliedQDMClicked());
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterLibrary().addClickHandler(event -> leftNavParameterClickEvent(event));
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefinitionLibrary().addClickHandler(event -> leftNavDefinitionClicked(event));
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getFunctionLibrary().addClickHandler(event -> leftNavFunctionClicked(event));
		cqlWorkspaceView.getCQLLeftNavBarPanelView().getCQLLibraryEditorTab().addClickHandler(event -> leftNavCQLLibraryEditorViewEvent());
	}

	private void addValueSetEventHandlers() {
		cqlWorkspaceView.getValueSetView().getCancelQDMButton().addClickHandler(event -> valueSetViewCancelQDMButtonClicked());
		cqlWorkspaceView.getValueSetView().getUpdateFromVSACButton().addClickHandler(event -> valueSetViewUpdateFromVSACClicked());
		cqlWorkspaceView.getValueSetView().getRetrieveFromVSACButton().addClickHandler(event -> valueSetViewRetrieveFromVSACClicked());
		cqlWorkspaceView.getValueSetView().getSaveButton().addClickHandler(event -> valueSetViewSaveButtonClicked());
		cqlWorkspaceView.getValueSetView().getUserDefinedInput().addValueChangeHandler(event -> valueSetViewUserDefinedInputChangedEvent());
		cqlWorkspaceView.getValueSetView().getClearButton().addClickHandler(event -> valueSetViewClearButtonClicked());
		cqlWorkspaceView.getValueSetView().getCopyButton().addClickHandler(event -> copyValueSets());
		cqlWorkspaceView.getValueSetView().getSelectAllButton().addClickHandler(event -> selectAllValueSets());
		cqlWorkspaceView.getValueSetView().getPasteButton().addClickHandler(event -> valueSetViewPasteClicked(event));
		cqlWorkspaceView.getValueSetView().getReleaseListBox().addChangeHandler(event -> valueSetViewReleaseListBoxChanged());
		cqlWorkspaceView.getValueSetView().getProgramListBox().addChangeHandler(event -> valueSetViewProgramListBoxChanged());
		cqlWorkspaceView.getValueSetView().getOIDInput().addValueChangeHandler(event -> clearOID());
		cqlWorkspaceView.getValueSetView().getOIDInput().sinkBitlessEvent("input");
		cqlWorkspaceView.getValueSetView().setObserver(new CQLAppliedValueSetView.Observer() {
			@Override
			public void onModifyClicked(CQLQualityDataSetDTO result) {
				if(!cqlWorkspaceView.getValueSetView().getIsLoading()){
					cqlWorkspaceView.resetMessageDisplay();
					cqlWorkspaceView.getValueSetView().resetCQLValuesetearchPanel();
					isModified = true;
					modifyValueSetDTO = result;
					String displayName = result.getName();
					// Substring at 60th character length.
					if(displayName.length() >=60){
						displayName = displayName.substring(0, 59);
					}
					HTML searchHeaderText = new HTML("<strong>Modify value set ( "+displayName +")</strong>");
					cqlWorkspaceView.getValueSetView().getSearchHeader().clear();
					cqlWorkspaceView.getValueSetView().getSearchHeader().add(searchHeaderText);
					cqlWorkspaceView.getValueSetView().getMainPanel().getElement().focus();
					if(result.getOid().equalsIgnoreCase(ConstantMessages.USER_DEFINED_QDM_OID)){
						isUserDefined = true;
					} else {
						isUserDefined = false;
					}

					onModifyValueSet(result, isUserDefined);
					cqlWorkspaceView.getValueSetView().getOIDInput().setFocus(true);
				}
			}

			@Override
			public void onDeleteClicked(CQLQualityDataSetDTO result, final int index) {
				if(!cqlWorkspaceView.getValueSetView().getIsLoading()){
					cqlWorkspaceView.resetMessageDisplay();
					cqlWorkspaceView.getValueSetView().resetCQLValuesetearchPanel();
					if((modifyValueSetDTO!=null) && modifyValueSetDTO.getId().equalsIgnoreCase(result.getId())){
						isModified = false;
					}
					String libraryId = MatContext.get().getCurrentCQLLibraryId();
					if ((libraryId != null) && !libraryId.equals(EMPTY_STRING)) {
						cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedValueSetObjId(result.getId());
						deleteConfirmationDialogBox.getMessageAlert().createAlert(buildSelectedToDeleteWithConfirmationMessage(VALUESET, result.getName()));
						deleteConfirmationDialogBox.show();
						cqlWorkspaceView.getValueSetView().getOIDInput().setFocus(true);
					}
				}
			}
		});
	}

	@Override
	protected void pasteValueSets() {
		cqlWorkspaceView.resetMessageDisplay();
		GlobalCopyPasteObject gbCopyPaste = MatContext.get().getGlobalCopyPaste();
		showSearchingBusy(true);
		if ((gbCopyPaste != null) && (gbCopyPaste.getCopiedValueSetList().size() > 0)) {
			List<CQLValueSetTransferObject> cqlValueSetTransferObjectsList = cqlWorkspaceView.getValueSetView().setMatValueSetListForValueSets(gbCopyPaste.getCopiedValueSetList(), appliedValueSetTableList);
			if (cqlValueSetTransferObjectsList.size() > 0) {

				MatContext.get().getLibraryService().saveValueSetList(cqlValueSetTransferObjectsList, appliedValueSetTableList, MatContext.get().getCurrentCQLLibraryId(), new AsyncCallback<CQLQualityDataModelWrapper>() {

					@Override
					public void onFailure(Throwable caught) {
						showSearchingBusy(false);
						Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
					}

					@Override
					public void onSuccess(CQLQualityDataModelWrapper result) {
						showSearchingBusy(false);
						if (result != null && result.getQualityDataDTO() != null) {
							setAppliedValueSetListInTable(result.getQualityDataDTO());
							messagePanel.getSuccessMessageAlert().createAlert(SUCCESSFULLY_VALUESET_PASTE);
						}
					}
				});
			} else {
				showSearchingBusy(false);
				messagePanel.getSuccessMessageAlert().createAlert(SUCCESSFULLY_VALUESET_PASTE);
			}
			
			cqlWorkspaceView.getValueSetView().clearSelectedCheckBoxes();
			MatContext.get().getGlobalCopyPaste().getCopiedValueSetList().clear();
		} else {
			showSearchingBusy(false);
			messagePanel.getWarningMessageAlert().createAlert(WARNING_PASTING_IN_VALUESET);
		}	 
	}

	@Override
	protected void pasteCodes() {
		cqlWorkspaceView.resetMessageDisplay();
		GlobalCopyPasteObject gbCopyPaste = MatContext.get().getGlobalCopyPaste();
		if( (gbCopyPaste != null) && (gbCopyPaste.getCopiedCodeList().size()>0) ){
			List<CQLCode> codesToPaste = cqlWorkspaceView.getCodesView().setMatCodeList(gbCopyPaste.getCopiedCodeList(), appliedCodeTableList);
			if(codesToPaste.size() > 0) {
				String cqlLibraryId = MatContext.get().getCurrentCQLLibraryId();
				showSearchingBusy(true);
				cqlService.saveCQLCodeListToCQLLibrary(codesToPaste, cqlLibraryId, new AsyncCallback<SaveUpdateCQLResult>() {
					@Override
					public void onFailure(Throwable caught) {
						Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
						showSearchingBusy(false);
					}

					@Override
					public void onSuccess(SaveUpdateCQLResult result) {
						messagePanel.getSuccessMessageAlert().createAlert(SUCCESSFULLY_PASTED_CODES_IN_MEASURE);
						cqlWorkspaceView.getCodesView().resetCQLCodesSearchPanel();
						appliedCodeTableList.clear();
						appliedCodeTableList.addAll(result.getCqlCodeList());
						MatContext.get().getCQLModel().setCodeList(appliedCodeTableList);
						cqlWorkspaceView.getCodesView().buildCodesCellTable(appliedCodeTableList, hasEditPermissions());
						cqlWorkspaceView.getCQLLeftNavBarPanelView().setCodeBadgeValue(appliedCodeTableList);
						if (result != null && result.getCqlModel().getAllValueSetAndCodeList() != null) {
							setAppliedValueSetListInTable(result.getCqlModel().getAllValueSetAndCodeList());
						}
						showSearchingBusy(false);
					}
				});
			} else {
				showSearchingBusy(false);
				messagePanel.getSuccessMessageAlert().createAlert(SUCCESSFULLY_PASTED_CODES_IN_MEASURE);
			}
			
			cqlWorkspaceView.getCodesView().clearSelectedCheckBoxes();
			MatContext.get().getGlobalCopyPaste().getCopiedCodeList().clear();
		} else {
			showSearchingBusy(false);
			messagePanel.getWarningMessageAlert().createAlert(CLIPBOARD_DOES_NOT_CONTAIN_CODES);
		}
	}

	private void addCodeSearchPanelHandlers() {
		cqlWorkspaceView.getCodesView().getCopyButton().addClickHandler(event -> copyCodes());
		cqlWorkspaceView.getCodesView().getSelectAllButton().addClickHandler(event -> selectAllCodes());
		cqlWorkspaceView.getCodesView().getPasteButton().addClickHandler(event -> pasteCodesClicked(event));
		cqlWorkspaceView.getCodesView().getClearButton().addClickHandler(event -> codesViewClearButtonClicked());
		cqlWorkspaceView.getCodesView().getRetrieveFromVSACButton().addClickHandler(event -> codesViewRetrieveFromVSACButtonClicked());
		cqlWorkspaceView.getCodesView().getApplyButton().addClickHandler(event -> codesViewSaveButtonClicked());
		cqlWorkspaceView.getCodesView().getCancelCodeButton().addClickHandler(event -> codesViewCancelButtonClicked());
		cqlWorkspaceView.getCodesView().setDelegator(new Delegator() {
			@Override
			public void onDeleteClicked(CQLCode result, int index) {
				if(!cqlWorkspaceView.getCodesView().getIsLoading()) {
					messagePanel.getSuccessMessageAlert().clearAlert();
					messagePanel.getErrorMessageAlert().clearAlert();
					if(result != null){
						cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedCodesObjId(result.getId());
						deleteConfirmationDialogBox.getMessageAlert().createAlert(buildSelectedToDeleteWithConfirmationMessage(CODE, result.getCodeOID()));
						deleteConfirmationDialogBox.show();
						cqlWorkspaceView.getCodesView().getCodeInput().setFocus(true);
					}
				} 
			}

			@Override
			public void onModifyClicked(CQLCode object) {
				if(!cqlWorkspaceView.getValueSetView().getIsLoading()){
					cqlWorkspaceView.resetMessageDisplay();
					cqlWorkspaceView.getCodesView().resetCQLCodesSearchPanel();
					isCodeModified = true;
					modifyCQLCode = object;
					cqlWorkspaceView.getCodesView().setValidateCodeObject(modifyCQLCode);
					String displayName = object.getCodeOID();
					// Substring at 60th character length.
					if(displayName.length() >=60){
						displayName = displayName.substring(0, 59);
					}
					HTML searchHeaderText = new HTML("<strong>Modify Code ( "+displayName +")</strong>");
					cqlWorkspaceView.getCodesView().getSearchHeader().clear();
					cqlWorkspaceView.getCodesView().getSearchHeader().add(searchHeaderText);
					cqlWorkspaceView.getCodesView().getMainPanel().getElement().focus();

					onModifyCode(object);
					cqlWorkspaceView.getCodesView().getCodeInput().setFocus(true);
				}
			}
		});
	}

	@Override
	protected void modifyCodes() {
		String cqlLibraryId = MatContext.get().getCurrentCQLLibraryId();	
		String codeName = cqlWorkspaceView.getCodesView().getCodeDescriptorInput().getValue();
		codeName = StringUtility.removeEscapedCharsFromString(codeName);
		CQLCode refCode = buildCQLCodeFromCodesView(codeName);
		refCode.setId(modifyCQLCode.getId());
		MatCodeTransferObject transferObject = cqlWorkspaceView.getCodesView().getCodeTransferObject(cqlLibraryId, refCode);		
		if (null != transferObject) {
			appliedCodeTableList.removeIf(code -> code.getId().equals(modifyCQLCode.getId()));
			if(!cqlWorkspaceView.getCodesView().checkCodeInAppliedCodeTableList(refCode.getDisplayName(), appliedCodeTableList)) {
				showSearchingBusy(true);
				cqlService.saveCQLCodestoCQLLibrary(transferObject, new AsyncCallback<SaveUpdateCQLResult>() {
					@Override
					public void onFailure(Throwable caught) {
						Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
						showSearchingBusy(false);
						appliedCodeTableList.add(modifyCQLCode);
					}

					@Override
					public void onSuccess(SaveUpdateCQLResult result) {
						messagePanel.getSuccessMessageAlert().createAlert(SUCCESSFUL_MODIFY_APPLIED_CODE);
						cqlWorkspaceView.getCodesView().resetCQLCodesSearchPanel();
						appliedCodeTableList.clear();
						List<CQLCode> codesToView = result.getCqlModel().getCodeList();				
						codesToView = codesToView.stream().filter(c -> c.getCodeIdentifier() != null && !c.getCodeIdentifier().isEmpty()).collect(Collectors.toList());
						appliedCodeTableList.addAll(codesToView);
						MatContext.get().getCQLModel().setCodeList(appliedCodeTableList);
						cqlWorkspaceView.getCQLLeftNavBarPanelView().setCodeBadgeValue(appliedCodeTableList);
						cqlWorkspaceView.getCodesView().buildCodesCellTable(appliedCodeTableList, hasEditPermissions());
						getAppliedValuesetAndCodeList();
						showSearchingBusy(false);
						cqlWorkspaceView.getCodesView().getApplyButton().setEnabled(false);
						isCodeModified = false;
						modifyCQLCode = null;
					}
				});
			} else {
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getDuplicateAppliedValueSetMsg(refCode.getDisplayName()));
			}
		}
	}

	@Override
	protected void addNewCodes() {
		String cqlLibraryId = MatContext.get().getCurrentCQLLibraryId();
		final String codeName = StringUtility.removeEscapedCharsFromString(cqlWorkspaceView.getCodesView().getCodeDescriptorInput().getValue());
		CQLCode refCode = buildCQLCodeFromCodesView(codeName);
		final String codeSystemName = refCode.getCodeSystemName();
		final String codeId = refCode.getCodeOID();
		MatCodeTransferObject transferObject = cqlWorkspaceView.getCodesView().getCodeTransferObject(cqlLibraryId, refCode);
		if (null != transferObject) {
			showSearchingBusy(true);
			cqlService.saveCQLCodestoCQLLibrary(transferObject, new AsyncCallback<SaveUpdateCQLResult>() {

				@Override
				public void onSuccess(SaveUpdateCQLResult result) {
					if(result.isSuccess()){
						messagePanel.getSuccessMessageAlert().createAlert(getCodeSuccessMessage(cqlWorkspaceView.getCodesView().getCodeInput().getText()));
						cqlWorkspaceView.getCodesView().resetCQLCodesSearchPanel();
						appliedCodeTableList.clear();
						List<CQLCode> codesToView = result.getCqlCodeList();
						codesToView = codesToView.stream().filter(c -> c.getCodeIdentifier() != null && !c.getCodeIdentifier().isEmpty()).collect(Collectors.toList());
						appliedCodeTableList.addAll(codesToView);
						cqlWorkspaceView.getCodesView().buildCodesCellTable(appliedCodeTableList, hasEditPermissions());
						cqlWorkspaceView.getCQLLeftNavBarPanelView().setCodeBadgeValue(appliedCodeTableList);
						getAppliedValuesetAndCodeList();
					} else {
						messagePanel.getSuccessMessageAlert().clearAlert();
						if(result.getFailureReason()==result.getDuplicateCode()){
							messagePanel.getErrorMessageAlert().createAlert(generateDuplicateErrorMessage(codeName));
						}

						else if(result.getFailureReason() ==  result.getBirthdateOrDeadError()) {
							messagePanel.getErrorMessageAlert().createAlert(getBirthdateOrDeadMessage(codeSystemName, codeId));
							cqlWorkspaceView.getCodesView().buildCodesCellTable(appliedCodeTableList, hasEditPermissions());
						}
					}
					showSearchingBusy(false);
					shiftFocusToCodeSearchPanel(result);
				}

				@Override
				public void onFailure(Throwable caught) {
					Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
					showSearchingBusy(false);
				}
			});
		}
	}

	@Override
	protected void updateVSACValueSets() {
		showSearchingBusy(true);
		String expansionId = null;

		cqlService.updateCQLVSACValueSets(MatContext.get().getCurrentCQLLibraryId(), expansionId, new AsyncCallback<VsacApiResult>() {
			@Override
			public void onFailure(final Throwable caught) {
				showSearchingBusy(false);
				Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
			}

			@Override
			public void onSuccess(final VsacApiResult result) {
				if (result.isSuccess()) {
					messagePanel.getSuccessMessageAlert().createAlert(VSAC_UPDATE_SUCCESSFULL);
					List<CQLQualityDataSetDTO> appliedListModel = new ArrayList<>();
					for (CQLQualityDataSetDTO cqlQDMDTO : result.getUpdatedCQLQualityDataDTOLIst()) {
						if (!ConstantMessages.DEAD_OID.equals(cqlQDMDTO.getDataType()) && !ConstantMessages.BIRTHDATE_OID.equals(cqlQDMDTO.getDataType())
								&& (cqlQDMDTO.getType() == null))  {
							appliedListModel.add(cqlQDMDTO);

							for(CQLQualityDataSetDTO cqlQualityDataSetDTO : appliedValueSetTableList){
								if(cqlQualityDataSetDTO.getId().equals(cqlQDMDTO.getId())){
									cqlQualityDataSetDTO.setOriginalCodeListName(cqlQDMDTO.getOriginalCodeListName());
									cqlQualityDataSetDTO.setName(cqlQDMDTO.getName());
								}
							}

							for(CQLIdentifierObject cqlIdentifierObject : MatContext.get().getValuesets()){
								if(cqlIdentifierObject.getId().equals(cqlQDMDTO.getId())){
									cqlIdentifierObject.setIdentifier(cqlQDMDTO.getName());
								}
							}

							for(CQLQualityDataSetDTO dataSetDTO : MatContext.get().getValueSetCodeQualityDataSetList()){
								if(dataSetDTO.getId().equals(cqlQDMDTO.getId())){
									dataSetDTO.setOriginalCodeListName(cqlQDMDTO.getOriginalCodeListName());
									dataSetDTO.setName(cqlQDMDTO.getName());
								}
							}
						}
					}
					cqlWorkspaceView.getValueSetView().buildAppliedValueSetCellTable(appliedListModel, hasEditPermissions());
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setAppliedQdmTableList(appliedValueSetTableList);
					cqlWorkspaceView.getCQLLeftNavBarPanelView().updateValueSetMap(appliedValueSetTableList);
				} else {
					messagePanel.getErrorMessageAlert().createAlert(convertMessage(result.getFailureReason()));
				}
				showSearchingBusy(false);
			}
		});
	}

	@Override
	protected void searchValueSetInVsac(String release, String expansionProfile) {
		showSearchingBusy(true);
		currentMatValueSet = null;
		final String oid = cqlWorkspaceView.getValueSetView().getOIDInput().getValue();
		if (!MatContext.get().isUMLSLoggedIn()) {
			messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getUMLS_NOT_LOGGEDIN());
			messagePanel.getErrorMessageAlert().setVisible(true);
			showSearchingBusy(false);
			return;
		}

		if ((oid == null) || oid.trim().isEmpty()) {
			messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getUMLS_OID_REQUIRED());
			messagePanel.getErrorMessageAlert().setVisible(true);
			showSearchingBusy(false);
			return;
		}

		vsacapiService.getMostRecentValueSetByOID(oid, release, expansionProfile, new AsyncCallback<VsacApiResult>() {
			@Override
			public void onFailure(final Throwable caught) {
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getVSAC_RETRIEVE_FAILED());
				messagePanel.getErrorMessageAlert().setVisible(true);
				showSearchingBusy(false);
			}
			@Override
			public void onSuccess(final VsacApiResult result) {
				if (result.isSuccess()) {
					List<MatValueSet> matValueSets = result.getVsacResponse();
					if (matValueSets != null) {						
						currentMatValueSet = matValueSets.get(0);
					}
					cqlWorkspaceView.getValueSetView().getOIDInput().setTitle(oid);
					cqlWorkspaceView.getValueSetView().getUserDefinedInput().setValue(currentMatValueSet.getDisplayName());
					cqlWorkspaceView.getValueSetView().getUserDefinedInput().setTitle(currentMatValueSet.getDisplayName());
					cqlWorkspaceView.getValueSetView().getSaveButton().setEnabled(true);					
					messagePanel.getSuccessMessageAlert().createAlert(getValuesetSuccessfulReterivalMessage(matValueSets.get(0).getDisplayName()));
					messagePanel.getSuccessMessageAlert().setVisible(true);
				} else {
					String message = convertMessage(result.getFailureReason());
					messagePanel.getErrorMessageAlert().createAlert(message);
					messagePanel.getErrorMessageAlert().setVisible(true);
				}
				showSearchingBusy(false);
			}
		});
	}

	@Override
	protected void updateAppliedValueSetsList(final MatValueSet matValueSet, final CodeListSearchDTO codeListSearchDTO, final CQLQualityDataSetDTO qualityDataSetDTO) {		
		CQLValueSetTransferObject matValueSetTransferObject = new CQLValueSetTransferObject();
		matValueSetTransferObject.setCqlLibraryId(MatContext.get().getCurrentCQLLibraryId());
		matValueSetTransferObject.setMatValueSet(matValueSet);
		matValueSetTransferObject.setCodeListSearchDTO(codeListSearchDTO);
		matValueSetTransferObject.setCqlQualityDataSetDTO(qualityDataSetDTO);
		matValueSetTransferObject.setAppliedQDMList(appliedValueSetTableList);
		matValueSetTransferObject.setUserDefinedText(cqlWorkspaceView.getValueSetView().getUserDefinedInput().getText());
		matValueSetTransferObject.scrubForMarkUp();
		showSearchingBusy(true);
		MatContext.get().getLibraryService().saveCQLValueset(matValueSetTransferObject, new AsyncCallback<SaveUpdateCQLResult>() {
			@Override
			public void onFailure(final Throwable caught) {
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
				showSearchingBusy(false);
				isModified = false;
				modifyValueSetDTO = null;
				currentMatValueSet = null;
				cqlWorkspaceView.getValueSetView().getSaveButton().setEnabled(false);
			}

			@Override
			public void onSuccess(final SaveUpdateCQLResult result) {
				if (result != null) {
					if (result.isSuccess()) {
						isModified = false;
						modifyValueSetDTO = null;
						currentMatValueSet = null;
						cqlWorkspaceView.getValueSetView().resetCQLValuesetearchPanel();
						messagePanel.getSuccessMessageAlert().createAlert(SUCCESSFUL_MODIFY_APPLIED_VALUESET);
						getAppliedValuesetAndCodeList();
					} else {
						if (result.getFailureReason() == SaveUpdateCodeListResult.ALREADY_EXISTS) {
							messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getDuplicateAppliedValueSetMsg(result.getCqlQualityDataSetDTO().getName()));
						} else if (result.getFailureReason() == SaveUpdateCodeListResult.SERVER_SIDE_VALIDATION) {
							messagePanel.getErrorMessageAlert().createAlert(INVALID_INPUT_DATA);
						}
					}
				}
				cqlWorkspaceView.getValueSetView().getSaveButton().setEnabled(false);
				showSearchingBusy(false);
			}
		});
	}

	@Override
	protected void addVSACCQLValueset() {
		String libraryID = MatContext.get().getCurrentCQLLibraryId();
		CQLValueSetTransferObject matValueSetTransferObject = createValueSetTransferObject(libraryID);
		matValueSetTransferObject.scrubForMarkUp();
		String originalCodeListName = matValueSetTransferObject.getMatValueSet().getDisplayName(); 
		String suffix = cqlWorkspaceView.getValueSetView().getSuffixInput().getValue();
		final String codeListName = (originalCodeListName!=null ? originalCodeListName : EMPTY_STRING) + (!suffix.isEmpty() ? " (" + suffix + ")" : EMPTY_STRING);
		saveValueset(matValueSetTransferObject, codeListName);
	}

	private void saveValueset(CQLValueSetTransferObject matValueSetTransferObject, final String codeListName) {
		if (!cqlWorkspaceView.getValueSetView().checkNameInValueSetList(codeListName,appliedValueSetTableList)) {
			showSearchingBusy(true);
			MatContext.get().getLibraryService().saveCQLValueset(matValueSetTransferObject,
					new AsyncCallback<SaveUpdateCQLResult>() {

				@Override
				public void onFailure(Throwable caught) {
					showSearchingBusy(false);
					if (!appliedValueSetTableList.isEmpty()) {
						appliedValueSetTableList.clear();
					}
					currentMatValueSet = null;
					cqlWorkspaceView.getValueSetView().getSaveButton().setEnabled(false);
				}

				@Override
				public void onSuccess(SaveUpdateCQLResult result) {
					String message = EMPTY_STRING;
					if (result != null) {
						if (result.isSuccess()) {
							message = getValuesetSuccessMessage(codeListName);
							MatContext.get().getEventBus().fireEvent(new QDSElementCreatedEvent(codeListName));
							cqlWorkspaceView.getValueSetView().resetCQLValuesetearchPanel();
							messagePanel.getSuccessMessageAlert().createAlert(message);
							previousIsProgramListBoxEnabled = isProgramListBoxEnabled;
							isProgramListBoxEnabled = true;
							loadProgramsAndReleases(); 
							getAppliedValuesetAndCodeList();
						} else {
							if (result.getFailureReason() == SaveUpdateCodeListResult.ALREADY_EXISTS) {
								messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getDuplicateAppliedValueSetMsg(result.getCqlQualityDataSetDTO().getName()));
							}
						}
					}
					currentMatValueSet = null;
					cqlWorkspaceView.getValueSetView().getSaveButton().setEnabled(false);
					showSearchingBusy(false);
				}
			});
		}  else {
			messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getDuplicateAppliedValueSetMsg(codeListName));
		}
	}

	@Override
	protected void addUserDefinedValueSet() {
		CQLValueSetTransferObject matValueSetTransferObject = createValueSetTransferObject(MatContext.get().getCurrentCQLLibraryId());
		matValueSetTransferObject.scrubForMarkUp();
		matValueSetTransferObject.setMatValueSet(null);
		matValueSetTransferObject.getCqlQualityDataSetDTO().setOid("");
		if ((matValueSetTransferObject.getUserDefinedText().length() > 0)) {
			ValueSetNameInputValidator valueSetNameInputValidator = new ValueSetNameInputValidator();
			String message = valueSetNameInputValidator.validate(matValueSetTransferObject);
			if (message.isEmpty()) {
				final String userDefinedInput = matValueSetTransferObject.getCqlQualityDataSetDTO().getName(); 
				saveValueset(matValueSetTransferObject, userDefinedInput);
			} else {
				messagePanel.getErrorMessageAlert().createAlert(message);
			}
		} else {
			messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getVALIDATION_MSG_ELEMENT_WITHOUT_VSAC());
		}
	}

	private CQLValueSetTransferObject createValueSetTransferObject(String libraryID) {
		if(currentMatValueSet == null) {
			currentMatValueSet = new MatValueSet();
		}
		CQLValueSetTransferObject matValueSetTransferObject = new CQLValueSetTransferObject();
		matValueSetTransferObject.setCqlLibraryId(libraryID);
		String originalCodeListName = cqlWorkspaceView.getValueSetView().getUserDefinedInput().getValue(); 
		matValueSetTransferObject.setCqlQualityDataSetDTO(new CQLQualityDataSetDTO());
		matValueSetTransferObject.getCqlQualityDataSetDTO().setOriginalCodeListName(originalCodeListName);
		matValueSetTransferObject.getCqlQualityDataSetDTO().setOid(currentMatValueSet.getID());

		if(!cqlWorkspaceView.getValueSetView().getSuffixInput().getValue().isEmpty()){
			matValueSetTransferObject.getCqlQualityDataSetDTO().setSuffix(cqlWorkspaceView.getValueSetView().getSuffixInput().getValue());
			matValueSetTransferObject.getCqlQualityDataSetDTO().setName(originalCodeListName+" ("+cqlWorkspaceView.getValueSetView().getSuffixInput().getValue()+")");
		} else {
			matValueSetTransferObject.getCqlQualityDataSetDTO().setName(originalCodeListName);
		}

		matValueSetTransferObject.getCqlQualityDataSetDTO().setRelease(EMPTY_STRING);
		String releaseValue = cqlWorkspaceView.getValueSetView().getReleaseListBox().getSelectedValue(); 
		if(!releaseValue.equalsIgnoreCase(MatContext.PLEASE_SELECT)) {
			matValueSetTransferObject.getCqlQualityDataSetDTO().setRelease(releaseValue);
		}

		matValueSetTransferObject.getCqlQualityDataSetDTO().setProgram(EMPTY_STRING);
		String programValue = cqlWorkspaceView.getValueSetView().getProgramListBox().getSelectedValue();
		if(!programValue.equalsIgnoreCase(MatContext.PLEASE_SELECT)) {
			matValueSetTransferObject.getCqlQualityDataSetDTO().setProgram(programValue);
		}

		CodeListSearchDTO codeListSearchDTO = new CodeListSearchDTO();
		codeListSearchDTO.setName(cqlWorkspaceView.getValueSetView().getUserDefinedInput().getText());
		matValueSetTransferObject.setCodeListSearchDTO(codeListSearchDTO);
		matValueSetTransferObject.setAppliedQDMList(appliedValueSetTableList);
		matValueSetTransferObject.setMatValueSet(currentMatValueSet);
		matValueSetTransferObject.setCqlLibraryId(libraryID);
		matValueSetTransferObject.setUserDefinedText(cqlWorkspaceView.getValueSetView().getUserDefinedInput().getText());
		return matValueSetTransferObject;
	}

	@Override
	protected void focusSkipLists() {
		Mat.focusSkipLists("CqlComposer");
	}

	@Override
	protected String getWorkspaceTitle() {
		return "CQL Library Workspace";
	}

	@Override
	protected void buildCQLView() {
		cqlWorkspaceView.getCQLLibraryEditorView().getCqlAceEditor().setText(EMPTY_STRING);
		showSearchingBusy(true);
		MatContext.get().getCQLLibraryService().getCQLLibraryFileData(MatContext.get().getCurrentCQLLibraryId(),
				new AsyncCallback<SaveUpdateCQLResult>() {
			@Override
			public void onSuccess(SaveUpdateCQLResult result) {
				if (result.isSuccess()) {
					buildCQLViewSuccess(result);
				}
				showSearchingBusy(false);
			}

			@Override
			public void onFailure(Throwable caught) {
				Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
				showSearchingBusy(false);
			}
		});
	}

	private void getAllIncludeLibraryList(final String searchText) {
		messagePanel.getErrorMessageAlert().clearAlert();
		messagePanel.getSuccessMessageAlert().clearAlert();
		messagePanel.getWarningMessageAlert().clearAlert();
		showSearchingBusy(true);

		MatContext.get().getCQLLibraryService().searchForIncludes(setId, cqlLibraryName, searchText, new AsyncCallback<SaveCQLLibraryResult>() {

			@Override
			public void onFailure(Throwable caught) {
				messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
				showSearchingBusy(false);
			}

			@Override
			public void onSuccess(SaveCQLLibraryResult result) {
				showSearchingBusy(false);
				if (result != null && result.getCqlLibraryDataSetObjects().size() > 0) {
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setIncludeLibraryList(result.getCqlLibraryDataSetObjects());
					cqlWorkspaceView.buildIncludesView();
					cqlWorkspaceView.getIncludeView().buildIncludeLibraryCellTable(result, hasEditPermissions(), false);
				} else {
					cqlWorkspaceView.buildIncludesView();
					cqlWorkspaceView.getIncludeView().buildIncludeLibraryCellTable(result, hasEditPermissions(), false);
					if (!cqlWorkspaceView.getIncludeView().getSearchTextBox().getText().isEmpty())
						messagePanel.getErrorMessageAlert().createAlert(NO_LIBRARIES_RETURNED);
				}

				if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox().getItemCount() >= CQLWorkSpaceConstants.VALID_INCLUDE_COUNT) {
					messagePanel.getWarningMessageAlert().createAlert(MatContext.get().getMessageDelegate().getCqlLimitWarningMessage());
				} else {
					messagePanel.getWarningMessageAlert().clearAlert();
				}
				cqlWorkspaceView.getIncludeView().getAliasNameTxtArea().setFocus(true);
			}
		});
	}

	@Override
	protected void setGeneralInformationViewEditable(boolean isEditable) {
		cqlWorkspaceView.getCqlGeneralInformationView().setIsEditable(hasEditPermissions() && isEditable);
	}

	private void getAttributesForDataType(final CQLFunctionArgument functionArg) {
		attributeService.getAllAttributesByDataType(functionArg.getQdmDataType(), new AsyncCallback<List<QDSAttributes>>() {
			@Override
			public void onFailure(Throwable caught) {
				Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
			}

			@Override
			public void onSuccess(List<QDSAttributes> result) {
				cqlWorkspaceView.getCQLLeftNavBarPanelView().setAvailableQDSAttributeList(result);
				AddFunctionArgumentDialogBox.showArgumentDialogBox(functionArg, true,cqlWorkspaceView.getCQLFunctionsView(), messagePanel, hasEditPermissions());
			}
		});
	}

	private void displayEmpty() {
		panel.clear();
		panel.add(emptyWidget);
	}

	@Override
	public Widget getWidget() {
		panel.setStyleName("contentPanel");
		return panel;
	}

	public CQLWorkspaceView getSearchDisplay() {
		return cqlWorkspaceView;
	}

	private void setAppliedValueSetListInTable(List<CQLQualityDataSetDTO> valueSetList) {
		appliedValueSetTableList.clear();
		List<CQLQualityDataSetDTO> allValuesets = new ArrayList<>();
		for (CQLQualityDataSetDTO dto : valueSetList) {
			allValuesets.add(dto);
		}
		MatContext.get().setValuesets(allValuesets);
		for (CQLQualityDataSetDTO valueset : allValuesets) {
			if((valueset.getOid().equals("419099009") || valueset.getOid().equals("21112-8") 
					|| (valueset.getType() !=null) && valueset.getType().equalsIgnoreCase("code"))
					|| appliedValueSetTableList.stream().filter(v -> v.getName().equals(valueset.getName())).count() > 0){
				continue;
			}
			appliedValueSetTableList.add(valueset);
		}
		cqlWorkspaceView.getValueSetView().buildAppliedValueSetCellTable(appliedValueSetTableList, hasEditPermissions());
		cqlWorkspaceView.getCQLLeftNavBarPanelView().updateValueSetMap(appliedValueSetTableList);
	}

	private void leftNavParameterNameListBoxDoubleClickEvent(DoubleClickEvent event) {
		if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getIsLoading()) {
			event.stopPropagation();
		} else {
			showSearchBusyOnDoubleClick(true);
			cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().clearAnnotations();
			cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().removeAllMarkers();
			parameterHideEvent();
			resetViewCQLCollapsiblePanel(cqlWorkspaceView.getCQLParametersView().getPanelViewCQLCollapse());

			cqlWorkspaceView.getCQLLeftNavBarPanelView().setIsDoubleClick(true);
			cqlWorkspaceView.getCQLLeftNavBarPanelView().setIsNavBarClick(false);
			if (getIsPageDirty()) {
				showSearchBusyOnDoubleClick(false);
				showUnsavedChangesWarning();
			} else {
				int selectedIndex = cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterNameListBox().getSelectedIndex();
				if (selectedIndex != -1) {
					final String selectedParamID = cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterNameListBox().getValue(selectedIndex);
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedParamerterObjId(selectedParamID);
					if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterMap().get(selectedParamID) != null) {
						cqlWorkspaceView.getCQLParametersView().getParameterButtonBar().getDeleteButton().setTitle("Delete");
						cqlWorkspaceView.getCQLParametersView().getParameterButtonBar().getDeleteButton().setEnabled(false);
						cqlWorkspaceView.getCQLParametersView().setWidgetReadOnly(false);
						cqlWorkspaceView.getCQLParametersView().getParameterButtonBar().getDeleteButton().setEnabled(false);
						cqlWorkspaceView.getCQLParametersView().getAddNewButtonBar().getaddNewButton().setEnabled(hasEditPermissions());
						MatContext.get().getCQLLibraryService().getUsedCqlArtifacts(MatContext.get().getCurrentCQLLibraryId(),
								new AsyncCallback<GetUsedCQLArtifactsResult>() {

							@Override
							public void onFailure(Throwable caught) {
								showSearchBusyOnDoubleClick(false);
								Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
							}

							@Override
							public void onSuccess(GetUsedCQLArtifactsResult result) {
								parameterListBoxDoubleClickEventSuccess(selectedParamID, result);
							}
						});
					} else {
						showSearchBusyOnDoubleClick(false);
					}
				} else {
					showSearchBusyOnDoubleClick(false);
				}
				cqlWorkspaceView.resetMessageDisplay();
			}
		}
		cqlWorkspaceView.getCQLParametersView().getMainParamViewVerticalPanel().setFocus(true);
	}

	private void parameterListBoxDoubleClickEventSuccess(final String selectedParamID, GetUsedCQLArtifactsResult result) {
		showSearchBusyOnDoubleClick(false);
		cqlWorkspaceView.getCQLParametersView().getParameterNameTxtArea().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterMap().get(selectedParamID).getName());
		cqlWorkspaceView.getCQLParametersView().getParameterAceEditor().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterMap().get(selectedParamID).getLogic());
		cqlWorkspaceView.getCQLParametersView().getParameterCommentTextArea().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterMap().get(selectedParamID).getCommentString());

		CQLParameter currentParameter = cqlWorkspaceView.getCQLLeftNavBarPanelView().getParameterMap().get(selectedParamID);
		if (hasEditPermissions()) {
			cqlWorkspaceView.getCQLParametersView().setWidgetReadOnly(true);
		}
		SharedCQLWorkspaceUtility.setCQLWorkspaceExceptionAnnotations(currentParameter.getName(), result.getCqlErrorsPerExpression(), result.getCqlWarningsPerExpression(), curAceEditor);
	}

	private void editIncludedLibraryDialogApplyButtonClicked(final EditIncludedLibraryDialogBox editIncludedLibraryDialogBox) {
		messagePanel.getErrorMessageAlert().clearAlert();
		messagePanel.getSuccessMessageAlert().clearAlert();
		if(editIncludedLibraryDialogBox.getSelectedList().size() > 0){
			final CQLIncludeLibrary toBeModified = cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludeLibraryMap().get(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedIncLibraryObjId());
			final CQLLibraryDataSetObject dto = editIncludedLibraryDialogBox.getSelectedList().get(0);
			if (dto != null) {
				final CQLIncludeLibrary currentObject = new CQLIncludeLibrary(dto);
				currentObject.setAliasName(toBeModified.getAliasName());
				MatContext.get().getCQLLibraryService().saveIncludeLibrayInCQLLookUp(
						MatContext.get().getCurrentCQLLibraryId(), toBeModified, currentObject,
						cqlWorkspaceView.getCQLLeftNavBarPanelView().getViewIncludeLibrarys(),
						new AsyncCallback<SaveUpdateCQLResult>() {

							@Override
							public void onFailure(Throwable arg0) {

								editIncludedLibraryDialogBox.getDialogModal().hide();
								editIncludedLibraryDialogBox.getErrorMessageAlert().clearAlert();
								Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
							}

							@Override
							public void onSuccess(SaveUpdateCQLResult result) {
								editIncludedLibraryDialogBox.getErrorMessageAlert().clearAlert();
								CQLAppliedValueSetUtility.loadReleases(cqlWorkspaceView.getValueSetView().getReleaseListBox(), cqlWorkspaceView.getValueSetView().getProgramListBox());
								if (result != null) {
									if (result.isSuccess()) {
										cqlWorkspaceView.getCQLLeftNavBarPanelView().setViewIncludeLibrarys(
												result.getCqlModel().getCqlIncludeLibrarys());
										cqlWorkspaceView.getCQLLeftNavBarPanelView().udpateIncludeLibraryMap();
										MatContext.get().setIncludes(getIncludesList(result.getCqlModel().getCqlIncludeLibrarys()));
										MatContext.get().setIncludedValues(result);
										MatContext.get().setCQLModel(result.getCqlModel());
										editIncludedLibraryDialogBox.getDialogModal().hide();
										DomEvent.fireNativeEvent(
												Document.get().createDblClickEvent(cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox().getSelectedIndex(), 0, 0, 0, 0, false, false, false,false),
												cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox());
										String libraryNameWithVersion = result.getIncludeLibrary().getCqlLibraryName() + " v" +result.getIncludeLibrary().getVersion();
										messagePanel.getSuccessMessageAlert().createAlert(libraryNameWithVersion + " has been successfully saved as the alias " + result.getIncludeLibrary().getAliasName());
									}
								}
							}
						});
			}
		} else {
			editIncludedLibraryDialogBox.getErrorMessageAlert().clearAlert();
			editIncludedLibraryDialogBox.getErrorMessageAlert().createAlert(NO_LIBRARY_TO_REPLACE);
		}
	}

	private void includeViewSaveModifyClicked() {
		messagePanel.getErrorMessageAlert().clearAlert();
		messagePanel.getSuccessMessageAlert().clearAlert();
		final EditIncludedLibraryDialogBox editIncludedLibraryDialogBox = new EditIncludedLibraryDialogBox("Replace Library");
		editIncludedLibraryDialogBox.findAvailableLibraries(currentIncludeLibrarySetId, currentIncludeLibraryId);
		editIncludedLibraryDialogBox.getApplyButton().addClickHandler(event -> editIncludedLibraryDialogApplyButtonClicked(editIncludedLibraryDialogBox));
	}

	private void listBoxKeyPress(KeyPressEvent event) {
		if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
			DomEvent.fireNativeEvent(Document.get().createDblClickEvent(cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox().getSelectedIndex(), 0, 0, 0, 0, false, false, false,false),cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox());
		}
	}

	private void cqlLeftNavBarIncludesNameListBoxDoubleClickEvent(DoubleClickEvent event) {
		if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getIsLoading()) {
			event.stopPropagation();
		} else {
			cqlWorkspaceView.getCQLLeftNavBarPanelView().setIsDoubleClick(true);
			cqlWorkspaceView.getCQLLeftNavBarPanelView().setIsNavBarClick(false);

			if (getIsPageDirty()) {
				showUnsavedChangesWarning();
			} else {
				int selectedIndex = cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox().getSelectedIndex();
				if (selectedIndex != -1) {
					final String selectedIncludeLibraryID = cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludesNameListbox().getValue(selectedIndex);
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedIncLibraryObjId(selectedIncludeLibraryID);
					if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludeLibraryMap().get(selectedIncludeLibraryID) != null) {
						MatContext.get().getCQLLibraryService().findCQLLibraryByID(
								cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludeLibraryMap().get(selectedIncludeLibraryID).getCqlLibraryId(),
								new AsyncCallback<CQLLibraryDataSetObject>() {

									@Override
									public void onSuccess(CQLLibraryDataSetObject result) {
										if (result != null) {
											currentIncludeLibrarySetId = result.getCqlSetId();
											currentIncludeLibraryId = result.getId();
											cqlWorkspaceView.getIncludeView().buildIncludesReadOnlyView();
											cqlWorkspaceView.getIncludeView().getAliasNameTxtArea().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludeLibraryMap().get(selectedIncludeLibraryID).getAliasName());
											cqlWorkspaceView.getIncludeView().getViewCQLEditor().setText(result.getCqlText());
											cqlWorkspaceView.getIncludeView().getOwnerNameTextBox().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getOwnerName(result));
											cqlWorkspaceView.getIncludeView().getCqlLibraryNameTextBox().setText(result.getCqlName());
											cqlWorkspaceView.getIncludeView().getSaveModifyButton().setEnabled(false);														
											cqlWorkspaceView.getIncludeView().getDeleteButton().setEnabled(false);
											cqlWorkspaceView.getIncludeView().getSaveModifyButton().setEnabled(false);
											if (hasEditPermissions()) {
												cqlWorkspaceView.getIncludeView().setWidgetReadOnly(false);
												cqlWorkspaceView.getIncludeView().getSaveModifyButton().setEnabled(true);

												MatContext.get().getCQLLibraryService().getUsedCqlArtifacts(
														MatContext.get().getCurrentCQLLibraryId(),
														new AsyncCallback<GetUsedCQLArtifactsResult>() {

															@Override
															public void onFailure(Throwable caught) {
																Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
															}

															@Override
															public void onSuccess(GetUsedCQLArtifactsResult result) {
																CQLIncludeLibrary cqlIncludeLibrary = cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludeLibraryMap().get(selectedIncludeLibraryID);
																AceEditor editor = cqlWorkspaceView.getIncludeView().getViewCQLEditor(); 
																editor.clearAnnotations();
																editor.removeAllMarkers();

																String formattedName = cqlIncludeLibrary.getCqlLibraryName() + "-" + cqlIncludeLibrary.getVersion();												
																List<CQLError> errorsForLibrary = result.getLibraryNameErrorsMap().get(formattedName);
																List<CQLError> warningsForLibrary = result.getLibraryNameWarningsMap().get(formattedName);											
																SharedCQLWorkspaceUtility.createCQLWorkspaceAnnotations(errorsForLibrary, SharedCQLWorkspaceUtility.ERROR_PREFIX, AceAnnotationType.ERROR, editor);
																SharedCQLWorkspaceUtility.createCQLWorkspaceAnnotations(warningsForLibrary, SharedCQLWorkspaceUtility.WARNING_PREFIX, AceAnnotationType.WARNING, editor);
																editor.setAnnotations();
																cqlWorkspaceView.getIncludeView().getDeleteButton().setEnabled(true);
															}
														});
											}
										}
									}

									@Override
									public void onFailure(Throwable caught) {
										messagePanel.getErrorMessageAlert().createAlert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
									}
								});
						cqlWorkspaceView.getIncludeView().setSelectedObject(cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludeLibraryMap().get(selectedIncludeLibraryID).getCqlLibraryId());
						cqlWorkspaceView.getIncludeView().setIncludedList(cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludedList(cqlWorkspaceView.getCQLLeftNavBarPanelView().getIncludeLibraryMap()));
						cqlWorkspaceView.getIncludeView().getSelectedObjectList().clear();
					}
				}
				cqlWorkspaceView.resetMessageDisplay();
			}
		}
	}

	private void definitionDeleteButtonClicked() {
		resetViewCQLCollapsiblePanel(cqlWorkspaceView.getCQLDefinitionsView().getPanelViewCQLCollapse());
		deleteConfirmationDialogBox.getMessageAlert().createAlert(buildSelectedToDeleteMessage(DEFINITION, cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea().getValue()));
		deleteConfirmationDialogBox.show();
	}

	private void cqlLeftNavBarDefineNameListBoxKeyPressed(KeyPressEvent event) {
		if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
			DomEvent.fireNativeEvent(Document.get().createDblClickEvent(cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefineNameListBox().getSelectedIndex(), 0, 0, 0, 0, false, false, false, false),cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefineNameListBox());
		}
	}

	private void cqlLeftNavBarDefineNameListBoxDoubleClickEvent(DoubleClickEvent event) {
		if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getIsLoading()) {
			event.stopPropagation();
		} else {
			showSearchBusyOnDoubleClick(true);
			cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().clearAnnotations();
			cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().removeAllMarkers();
			resetAceEditor(cqlWorkspaceView.getCQLDefinitionsView().getViewCQLAceEditor());
			resetViewCQLCollapsiblePanel(cqlWorkspaceView.getCQLDefinitionsView().getPanelViewCQLCollapse());
			cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
			cqlWorkspaceView.getCQLLeftNavBarPanelView().setIsDoubleClick(true);
			if (getIsPageDirty()) {
				showSearchBusyOnDoubleClick(false);
				showUnsavedChangesWarning();
			} else {
				int selectedIndex = cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefineNameListBox().getSelectedIndex();
				if (selectedIndex != -1) {
					final String selectedDefinitionID = cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefineNameListBox().getValue(selectedIndex);
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedDefinitionObjId(selectedDefinitionID);

					if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefinitionMap().get(selectedDefinitionID) != null) {
						cqlWorkspaceView.getCQLDefinitionsView().getDefineButtonBar().getDeleteButton().setTitle("Delete");

						cqlWorkspaceView.getCQLDefinitionsView().getDefineButtonBar().getDeleteButton().setEnabled(false);
						cqlWorkspaceView.getCQLDefinitionsView().setWidgetReadOnly(false);
						cqlWorkspaceView.getCQLDefinitionsView().getAddNewButtonBar().getaddNewButton().setEnabled(hasEditPermissions());

						MatContext.get().getCQLLibraryService().getUsedCqlArtifacts(MatContext.get().getCurrentCQLLibraryId(),
								new AsyncCallback<GetUsedCQLArtifactsResult>() {

							@Override
							public void onFailure(Throwable caught) {
								showSearchBusyOnDoubleClick(false);
								cqlWorkspaceView.getCQLDefinitionsView().setWidgetReadOnly(hasEditPermissions());
								Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
							}

							@Override
							public void onSuccess(GetUsedCQLArtifactsResult result) {
								definitionListBoxDoubleClickEventSuccess(selectedDefinitionID, result);
							}
						});

					} else {
						showSearchBusyOnDoubleClick(false);
					}
				}else {
					showSearchBusyOnDoubleClick(false);
				}

				cqlWorkspaceView.resetMessageDisplay();
			}
		}
		cqlWorkspaceView.getCQLDefinitionsView().getMainDefineViewVerticalPanel().setFocus(true);
	}

	private void definitionListBoxDoubleClickEventSuccess(final String selectedDefinitionID, GetUsedCQLArtifactsResult result) {
		showSearchBusyOnDoubleClick(false);
		CQLDefinition currentDefinition = cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefinitionMap().get(selectedDefinitionID);
		cqlWorkspaceView.getCQLDefinitionsView().getDefineNameTxtArea().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefinitionMap().get(selectedDefinitionID).getName());
		cqlWorkspaceView.getCQLDefinitionsView().getDefineAceEditor().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefinitionMap().get(selectedDefinitionID).getLogic());
		cqlWorkspaceView.getCQLDefinitionsView().getDefineCommentTextArea().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getDefinitionMap().get(selectedDefinitionID).getCommentString());
		
		if (hasEditPermissions()) {
			cqlWorkspaceView.getCQLDefinitionsView().setWidgetReadOnly(true);
		}
		
		SharedCQLWorkspaceUtility.setCQLWorkspaceExceptionAnnotations(currentDefinition.getName(), result.getCqlErrorsPerExpression(), result.getCqlWarningsPerExpression(), curAceEditor);
		if(result.getCqlErrors().isEmpty() && result.getExpressionReturnTypeMap() != null){
			cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setText(result.getExpressionReturnTypeMap().get(currentDefinition.getName()));
			cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression is "+result.getExpressionReturnTypeMap().get(currentDefinition.getName()));
		} else {
			cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
			cqlWorkspaceView.getCQLDefinitionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression");
		}
	}

	private void includeViewSearchButtonClicked() {
		getAllIncludeLibraryList(cqlWorkspaceView.getIncludeView().getSearchTextBox().getText().trim());
		cqlWorkspaceView.getIncludeView().getAliasNameTxtArea().setFocus(true);
	}

	private void includesViewDeleteButtonClicked() {
		deleteConfirmationDialogBox.getMessageAlert().createAlert(buildSelectedToDeleteWithConfirmationMessage(LIBRARY, cqlWorkspaceView.getIncludeView().getAliasNameTxtArea().getValue()));
		deleteConfirmationDialogBox.show();
		cqlWorkspaceView.getIncludeView().getAliasNameTxtArea().setFocus(true);
	}

	private void leftNavBarFuncNameListBoxKeyPressedEvent(KeyPressEvent event) {
		if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
			DomEvent.fireNativeEvent(
					Document.get().createDblClickEvent(cqlWorkspaceView.getCQLLeftNavBarPanelView()
							.getFuncNameListBox().getSelectedIndex(), 0, 0, 0, 0, false, false, false,
							false),
					cqlWorkspaceView.getCQLLeftNavBarPanelView().getFuncNameListBox());
		}
	}

	private void leftNavBarFuncNameListBoxDoubleClickedEvent(DoubleClickEvent event) {
		if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getIsLoading()) {
			event.stopPropagation();
		} else {
			showSearchBusyOnDoubleClick(true);
			cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().clearAnnotations();
			cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().removeAllMarkers();
			cqlWorkspaceView.getCQLLeftNavBarPanelView().setIsDoubleClick(true);
			cqlWorkspaceView.getCQLLeftNavBarPanelView().setIsNavBarClick(false);

			resetAceEditor(cqlWorkspaceView.getCQLFunctionsView().getViewCQLAceEditor());
			resetViewCQLCollapsiblePanel(cqlWorkspaceView.getCQLFunctionsView().getPanelViewCQLCollapse());

			cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setText(EMPTY_STRING);


			if (getIsPageDirty()) {
				showSearchBusyOnDoubleClick(false);
				showUnsavedChangesWarning();
			} else {
				int selectedIndex = cqlWorkspaceView.getCQLLeftNavBarPanelView().getFuncNameListBox().getSelectedIndex();
				if (selectedIndex != -1) {
					final String selectedFunctionId = cqlWorkspaceView.getCQLLeftNavBarPanelView().getFuncNameListBox().getValue(selectedIndex);
					cqlWorkspaceView.getCQLLeftNavBarPanelView().setCurrentSelectedFunctionObjId(selectedFunctionId);

					if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getFunctionMap().get(selectedFunctionId) != null) {
						cqlWorkspaceView.getCQLFunctionsView().getFunctionButtonBar().getDeleteButton().setEnabled(false);
						cqlWorkspaceView.getCQLFunctionsView().setWidgetReadOnly(false);
						cqlWorkspaceView.getCQLFunctionsView().getAddNewButtonBar().getaddNewButton().setEnabled(hasEditPermissions());
						// load most recent used cql artifacts
						MatContext.get().getCQLLibraryService().getUsedCqlArtifacts(MatContext.get().getCurrentCQLLibraryId(),
								new AsyncCallback<GetUsedCQLArtifactsResult>() {

							@Override
							public void onFailure(Throwable caught) {
								showSearchingBusy(false);
								cqlWorkspaceView.getCQLFunctionsView().setWidgetReadOnly(hasEditPermissions());
								Window.alert(MatContext.get().getMessageDelegate().getGenericErrorMessage());
							}

							@Override
							public void onSuccess(GetUsedCQLArtifactsResult result) {
								functionListBoxDoubleClickEventSuccess(selectedFunctionId, result);
							}

						});

					} else {
						showSearchBusyOnDoubleClick(false);
					}
				} else {
					showSearchBusyOnDoubleClick(false);
				}
				if (cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedFunctionObjId() != null) {
					CQLFunctions selectedFunction = cqlWorkspaceView.getCQLLeftNavBarPanelView().getFunctionMap().get(cqlWorkspaceView.getCQLLeftNavBarPanelView().getCurrentSelectedFunctionObjId());
					if (selectedFunction.getArgumentList() != null) {
						cqlWorkspaceView.getCQLFunctionsView().getFunctionArgumentList().clear();
						cqlWorkspaceView.getCQLFunctionsView().getFunctionArgumentList().addAll(selectedFunction.getArgumentList());
					} else {
						cqlWorkspaceView.getCQLFunctionsView().getFunctionArgumentList().clear();
					}
				}
				cqlWorkspaceView.resetMessageDisplay();
			}
			cqlWorkspaceView.getCQLFunctionsView().createAddArgumentViewForFunctions(cqlWorkspaceView.getCQLFunctionsView().getFunctionArgumentList(), hasEditPermissions());
		}
		cqlWorkspaceView.getCQLFunctionsView().getMainFunctionVerticalPanel().setFocus(true);
	}

	private void functionListBoxDoubleClickEventSuccess(final String selectedFunctionId, GetUsedCQLArtifactsResult result) {
		showSearchBusyOnDoubleClick(false);
		cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getFunctionMap().get(selectedFunctionId).getName());
		cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getFunctionMap().get(selectedFunctionId).getLogic());
		cqlWorkspaceView.getCQLFunctionsView().getFunctionCommentTextArea().setText(cqlWorkspaceView.getCQLLeftNavBarPanelView().getFunctionMap().get(selectedFunctionId).getCommentString());

		CQLFunctions currentFunction = cqlWorkspaceView.getCQLLeftNavBarPanelView().getFunctionMap().get(selectedFunctionId);
		if (MatContext.get().getLibraryLockService().checkForEditPermission()) {
			cqlWorkspaceView.getCQLFunctionsView().setWidgetReadOnly(true);
		}

		SharedCQLWorkspaceUtility.setCQLWorkspaceExceptionAnnotations(currentFunction.getName(), result.getCqlErrorsPerExpression(), 
				result.getCqlWarningsPerExpression(), cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor());

		if(result.getCqlErrors().isEmpty() && result.getExpressionReturnTypeMap() != null){
			cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setText(result.getExpressionReturnTypeMap().get(currentFunction.getName()));
			cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression is "+result.getExpressionReturnTypeMap().get(currentFunction.getName()));

		} else {
			cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setText(EMPTY_STRING);
			cqlWorkspaceView.getCQLFunctionsView().getReturnTypeTextBox().setTitle("Return Type of CQL Expression");
		}
	}

	private void functionsViewInsertButtonClicked() {
		buildInsertPopUp();
		cqlWorkspaceView.getCQLFunctionsView().getFunctionBodyAceEditor().focus();
	}

	private void functionDeleteClicked() {
		resetViewCQLCollapsiblePanel(cqlWorkspaceView.getCQLFunctionsView().getPanelViewCQLCollapse());
		deleteConfirmationDialogBox.getMessageAlert().createAlert(buildSelectedToDeleteMessage(FUNCTION, cqlWorkspaceView.getCQLFunctionsView().getFuncNameTxtArea().getValue()));
		deleteConfirmationDialogBox.show();
	}

	private void valueSetViewCancelQDMButtonClicked() {
		cqlWorkspaceView.resetMessageDisplay();
		isModified = false;
		cqlWorkspaceView.getValueSetView().resetCQLValuesetearchPanel();
		cqlWorkspaceView.getValueSetView().getOIDInput().setFocus(true);

		previousIsProgramListBoxEnabled = isProgramListBoxEnabled;
		isProgramListBoxEnabled = true; 

		loadProgramsAndReleases(); 
		alert508StateChanges();
	}

	@Override
	protected void exportErrorFile() {
		if(hasEditPermissions()) {
			String url = GWT.getModuleBaseURL() + "export?libraryid=" + MatContext.get().getCurrentCQLLibraryId() + "&format=errorFileStandAlone";
			Window.open(url + "&type=save", "_self", "");
		}
	}

	@Override
	public CQLWorkspaceView getCQLWorkspaceView() {
		return cqlWorkspaceView;
	}

	@Override
	protected void componentsEvent() {}

	@Override
	protected boolean hasEditPermissions() {
		return MatContext.get().getLibraryLockService().checkForEditPermission();
	}

	@Override
	public boolean isStandaloneCQLLibrary() {
		return true;
	}
}