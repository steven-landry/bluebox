<?xml version="1.0" encoding="UTF-8" ?>
<%@ page language="java" pageEncoding="utf-8"
	contentType="text/html;charset=utf-8"%>
<%@ page import="java.util.ResourceBundle"%>
<%@ page import="com.bluebox.smtp.storage.StorageIf"%>
<%@ page import="com.bluebox.Config"%>
<%@ page import="com.bluebox.rest.AdminResource"%>
<%@ page import="com.bluebox.rest.StatsResource"%>
<%@ page import="com.bluebox.smtp.BlueBoxSMTPServer"%>
<%@ page import="com.bluebox.Utils"%>
<%@ page import="com.bluebox.smtp.Inbox"%>
<%
	Config bbconfig = Config.getInstance();
	ResourceBundle headerResource = ResourceBundle.getBundle("header",
			request.getLocale());
	ResourceBundle adminResource = ResourceBundle.getBundle("admin",
			request.getLocale());
%>

<!DOCTYPE html>
<html lang="en-US">
<head>
<title><%=headerResource.getString("welcome")%></title>
<jsp:include page="dojo.jsp" />
<script>		
		
		require(["dojo/parser", "dijit/ProgressBar", "dijit/form/Button", "dijit/form/NumberTextBox","dijit/form/HorizontalSlider","dijit/form/HorizontalRule","dijit/form/HorizontalRuleLabels"]);
		
		// start the refresh timer
		require(["dojox/timing"], function(registry){
			var t = new dojox.timing.Timer(5000);
			t.onTick = function() {
				updateWorkers();
			};
			t.start();
		});
		
		function updateWorkers() {
			try {
				require(["dojox/data/JsonRestStore"], function () {
					var urlStr = "<%=request.getContextPath()%>/jaxrs<%=StatsResource.PATH%>/workerstatus";
					var jStore = new dojox.data.JsonRestStore({target:urlStr,syncMode:false});
					jStore.fetch({
						  onComplete : 
							  	function(queryResults, request) {
								  try {
									  if (queryResults.<%=Inbox.BACKUP_WORKER%>) {
											backup.set({value: queryResults.<%=Inbox.BACKUP_WORKER%>});
											document.getElementById("<%=Inbox.BACKUP_WORKER%>Label").innerHTML = queryResults.<%=Inbox.BACKUP_WORKER%>_status;
									  }
									  if (queryResults.<%=Inbox.RESTORE_WORKER%>) {
											restore.set({value: queryResults.<%=Inbox.RESTORE_WORKER%>});
											document.getElementById("<%=Inbox.RESTORE_WORKER%>Label").innerHTML = queryResults.<%=Inbox.RESTORE_WORKER%>_status;
									  }
									  if (queryResults.<%=StorageIf.RAWCLEAN%>) {
											rawclean.set({value: queryResults.<%=StorageIf.RAWCLEAN%>});
											document.getElementById("<%=StorageIf.RAWCLEAN%>Label").innerHTML = queryResults.<%=StorageIf.RAWCLEAN%>_status;
									  }
									  if (queryResults.<%=Inbox.REINDEX_WORKER%>) {
											reindex.set({value: queryResults.<%=Inbox.REINDEX_WORKER%>});
											document.getElementById("<%=Inbox.REINDEX_WORKER%>Label").innerHTML = queryResults.<%=Inbox.REINDEX_WORKER%>_status;
									  }
									  if (queryResults.<%=Inbox.DBMAINTENANCE_WORKER%>) {
										  dbmaintenance.set({value: queryResults.<%=Inbox.DBMAINTENANCE_WORKER%>});
										  document.getElementById("<%=Inbox.DBMAINTENANCE_WORKER%>Label").innerHTML = queryResults.<%=Inbox.DBMAINTENANCE_WORKER%>_status;
									  }
									  if (queryResults.<%=Inbox.TRIM_WORKER%>) {
										  trim.set({value: queryResults.<%=Inbox.TRIM_WORKER%>});
										  document.getElementById("<%=Inbox.TRIM_WORKER%>Label").innerHTML = queryResults.<%=Inbox.TRIM_WORKER%>_status;
									  }
									  if (queryResults.<%=Inbox.EXPIRE_WORKER%>) {
										  expire.set({value: queryResults.<%=Inbox.EXPIRE_WORKER%>});
										  document.getElementById("<%=Inbox.EXPIRE_WORKER%>Label").innerHTML = queryResults.<%=Inbox.EXPIRE_WORKER%>_status;
									  }
									  if (queryResults.<%=Inbox.GENERATE_WORKER%>) {
										  generate.set({value: queryResults.<%=Inbox.GENERATE_WORKER%>});
										  document.getElementById("<%=Inbox.GENERATE_WORKER%>Label").innerHTML = queryResults.<%=Inbox.GENERATE_WORKER%>_status;
									  }
								  }
								  catch (err) {
									  console.log("page not ready :"+err);
								  }
								},
							onError :
								function(error) {
									console.log(error);
								}
					});
				});
			}
			catch (err) {
				alert(err);
			}
		}
		
		function generateEmails() {
			console.log("Generating "+dijit.byId("mailCountSlider").value+" emails");
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/generate/"+dijit.byId("mailCountSlider").value,
					"Scheduled generation of "+dijit.byId("mailCountSlider").value+" emails");
		}

		function setBaseCount() {
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/setbasecount/"+dijit.byId("setbasecount").value,
					"<%=adminResource.getString("set_global_action")%>");
		}
		
		function setSMTPBlacklist() {
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/setsmtpblacklist/"+dijit.byId("setsmtpblacklist").value,
					"<%=adminResource.getString("set_smtpblacklist_action")%>");
		}
		
		function deleteAllMail() {
			genericConfirmGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/clear",
					"<%=adminResource.getString("delete_all_action")%>");
		}
		
		function purgeDeletedMail() {
			genericConfirmGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/purge",
					"<%=adminResource.getString("purge_deleted_action")%>");
		}
		
		function clearErrorLogs() {
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/clearerrors",
					"<%=adminResource.getString("clear_errors_action")%>");
		}
		
		function trimMail() {
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/trim",
					"<%=adminResource.getString("prune_action")%>");
		}
		
		function expireMail() {
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/expire",
					"<%=adminResource.getString("expire_action")%>");
		}
		
		function rebuildSearchIndexes() {
			genericConfirmGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/rebuildsearchindexes",
					"<%=adminResource.getString("rebuild_search_action")%>",
					"Started");
		}
		
		function dbMaintenance() {
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/maintenance",
					"<%=adminResource.getString("db_maintenance_action")%>");
		}
		
		function startSMTP() {
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/startsmtp",
					"<%=adminResource.getString("start_smtp_action")%>");
		}
		
		function stopSMTP() {
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/stopsmtp",
					"<%=adminResource.getString("stop_smtp_action")%>");
		}
		
		function dbBackup() {
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/backup",
					"<%=adminResource.getString("backup_action")%>");
		}
		
		function dbRestore() {
			genericConfirmGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/restore",
					"<%=adminResource.getString("restore_action")%>",
					"Server responded");
		}
		
		function dbRawClean() {
			genericGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/rawclean",
					"<%=adminResource.getString("rawclean_action")%>",
					"Server responded");
		}
		
		function dbClean() {
			genericConfirmGet("<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/clean",
					"<%=adminResource.getString("clear_backup_action")%>");
		}
		
		function genericConfirmGet(url,title) {
			require(["dijit/ConfirmDialog", "dojo/domReady!"], function(ConfirmDialog){
			    myDialog = new ConfirmDialog({
			        title: "<%=adminResource.getString("confirm_title")%>",
				content : title,
				style : "width: 300px",
				onExecute : function() { //Callback function 
					genericGet(url, title);
				},
				onCancel : function() {
					console.log("Event Cancelled");
				}
			});
			myDialog.show();
		});
	}

	function genericGet(url, title) {
		console.log("Executing "+url);
		dojo.ready(function() {
			// The parameters to pass to xhrGet, the url, how to handle it, and the callbacks.
			var xhrArgs = {
				url : url,
				handleAs : "text",
				load : function(data) {
					// TODO - implement status message overlay animation
					showMessage(title + ":" + data);
				},
				error : function(error) {
					console.log("An unexpected error occurred: " + error);
					//dialog(title,error);
					showMessage(title + ":" + error);
				}
			};

			// Call the asynchronous xhrGet
			dojo.xhrGet(xhrArgs);
		});
	}

	require([ "dojo/domReady!" ], function() {
		selectMenu("admin");
		updateWorkers();
	});
</script>
</head>
<body class="<%=bbconfig.getString("dojo_style")%>">
	<div class="headerCol"><jsp:include page="menu.jsp" /></div>
	<div class="colWrapper">
		<div class="leftCol">
			<h2><%=adminResource.getString("title")%></h2>
		</div>

		<div class="centerCol">
			<div style="text-align: left;">
				<table>
					<tr>
						<td><label><%=adminResource.getString("generate_action")%></label></td>
						<td>
							<div id="mailCountSlider" style="width: 100%;"
								name="horizontalSlider"
								data-dojo-type="dijit/form/HorizontalSlider"
								data-dojo-props="value:100,
						    minimum: 0,
						    maximum:50000,
						    discreteValues:1001,
						    value:100,
						    intermediateChanges:false,
						    showButtons:false">
								<div data-dojo-type="dijit/form/HorizontalRule" container="bottomDecoration" count=5 style="height: 0.75em;"></div>
								<ol data-dojo-type="dijit/form/HorizontalRuleLabels"
									container="bottomDecoration"
									style="height: 1em; font-size: 75%; color: gray;">
									<li>0</li>
									<li>12500</li>
									<li>25000</li>
									<li>38000</li>
									<li>50000</li>
								</ol>
							</div>
						</td>
						<td><button onclick="generateEmails();"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
						<td><div data-dojo-type="dijit/ProgressBar"
								style="width: 100%" data-dojo-id="generate"
								id="generateProgress" data-dojo-props="maximum:100"></div></td>
						<td></td>
						<td align="right"><label data-dojo-id="<%=Inbox.GENERATE_WORKER%>label"
							id="<%=Inbox.GENERATE_WORKER%>Label"></label></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<% if (BlueBoxSMTPServer.getInstance(null).isRunning())  {%>
					<tr>
						<td><label><%=adminResource.getString("stop_smtp_action")%></label></td>
						<td></td>
						<td><button onclick="stopSMTP()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
					</tr>
					<% } else { %>
					<tr>
						<td><label><%=adminResource.getString("start_smtp_action")%></label></td>
						<td></td>
						<td><button onclick="startSMTP()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
					</tr>
					<% } %>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("delete_all_action")%></label></td>
						<td></td>
						<td><button onclick="deleteAllMail()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("purge_deleted_action")%></label></td>
						<td></td>
						<td><button onclick="purgeDeletedMail()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("clear_errors_action")%></label></td>
						<td></td>
						<td><button onclick="clearErrorLogs()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("prune_action")%></label></td>
						<td></td>
						<td><button onclick="trimMail()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
						<td>
							<div data-dojo-type="dijit/ProgressBar"
								style="width: 100%" data-dojo-id="<%=Inbox.TRIM_WORKER%>" id="<%=Inbox.TRIM_WORKER%>Progress"
								data-dojo-props="maximum:100"></div>
						</td>
						<td></td>
						<td align="right"><label data-dojo-id="<%=Inbox.TRIM_WORKER%>label" id="<%=Inbox.TRIM_WORKER%>Label"></label></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("expire_action")%></label></td>
						<td></td>
						<td><button onclick="expireMail()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
						<td><div data-dojo-type="dijit/ProgressBar"
								style="width: 100%" data-dojo-id="expire" id="expireProgress"
								data-dojo-props="maximum:100"></div></td>
						<td></td>
						<td align="right">
							<label data-dojo-id="<%=Inbox.EXPIRE_WORKER%>label"	id="<%=Inbox.EXPIRE_WORKER%>Label"></label>
						</td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("set_global_action")%></label></td>
						<td>
							<form method="get">
								<input id="setbasecount" type="text"
									data-dojo-type="dijit/form/NumberTextBox" name="setbasecount"
									value="25000000"
									data-dojo-props="constraints:{pattern: '#',min:0,max:99999999,places:0},  invalidMessage:'Please enter a value between 10 and 5000'" />
							</form>
						</td>
						<td><button onclick="setBaseCount();"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("rebuild_search_action")%></label></td>
						<td></td>
						<td><button onclick="rebuildSearchIndexes();"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
						<td><div data-dojo-type="dijit/ProgressBar"
								style="width: 100%" data-dojo-id="reindex" id="reindexProgress"
								data-dojo-props="maximum:100"></div></td>
						<td></td>
						<td align="right"><label data-dojo-id="<%=Inbox.REINDEX_WORKER%>label"
							id="<%=Inbox.REINDEX_WORKER%>Label"></label></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("db_maintenance_action")%></label></td>
						<td></td>
						<td><button onclick="dbMaintenance()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
						<td><div data-dojo-type="dijit/ProgressBar"
								style="width: 100%" data-dojo-id="dbmaintenance"
								id="dbmaintenanceProgress" data-dojo-props="maximum:100"></div></td>
						<td></td>
						<td align="right"><label data-dojo-id="dbmaintenancelabel"
							id="dbmaintenanceLabel"></label></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("backup_action")%></label></td>
						<td></td>
						<td><button onclick="dbBackup()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
						<td><div data-dojo-type="dijit/ProgressBar"
								style="width: 100%" data-dojo-id="backup" id="backupProgress"
								data-dojo-props="maximum:100"></div></td>
						<td></td>
						<td align="right"><label data-dojo-id="backuplabel"
							id="backupLabel"></label></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("restore_action")%></label></td>
						<td></td>
						<td><button onclick="dbRestore()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
						<td><div data-dojo-type="dijit/ProgressBar"
								style="width: 100%" data-dojo-id="restore" id="restoreProgress"
								data-dojo-props="maximum:100"></div></td>
						<td></td>
						<td align="right"><label data-dojo-id="restorelabel"
							id="restoreLabel"></label></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("rawclean_action")%></label></td>
						<td></td>
						<td><button onclick="dbRawClean()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
						<td><div data-dojo-type="dijit/ProgressBar"
								style="width: 100%" data-dojo-id="rawclean"
								id="rawcleanProgress" data-dojo-props="maximum:100"></div></td>
						<td></td>
						<td align="right"><label data-dojo-id="rawcleanlabel"
							id="rawcleanLabel"></label></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("clear_backup_action")%></label></td>
						<td></td>
						<td><button onclick="dbClean()"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
					</tr>
					<tr>
						<td><br /></td>
					</tr>
					<tr>
						<td><label><%=adminResource.getString("set_smtpblacklist_action")%></label></td>
						<td>
							<form method="get"
								action="<%=request.getContextPath()%>/jaxrs<%=AdminResource.PATH%>/setsmtpblacklist">
								<input id="setsmtpblacklist" type="text"
									data-dojo-type="dijit/form/TextBox" name="setsmtpblacklist"
									value="<%= Utils.toCSVString(Inbox.getInstance().getSMTPBlacklist()) %>" />
							</form>
						</td>
						<td><button onclick="setSMTPBlacklist();"
								data-dojo-type="dijit/form/Button" type="button"><%=adminResource.getString("execute")%></button></td>
					</tr>
				</table>
			</div>
		</div>

		<div class="rightCol">
			<jsp:include page="stats.jsp" />
		</div>
	</div>
</body>
</html>