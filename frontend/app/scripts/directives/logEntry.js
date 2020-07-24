(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('logEntry', function($uibModal, HtmlSanitizer, PaginatedQuerySrv, TaskLogSrv, UserSrv, NotificationSrv) {
            return {
                templateUrl: 'views/directives/log-entry.html',
                controller: function($scope, CortexSrv, PaginatedQuerySrv) {
                    $scope.showActions = false;
                    $scope.actions = null;
                    $scope.logResponders = null;
                    $scope.getLogResponders = function(taskLog, force) {
                        if(!force && $scope.logResponders !== null) {
                           return;
                        }

                        $scope.logResponders = null;
                        CortexSrv.getResponders('case_task_log', taskLog._id)
                            .then(function(responders) {
                                $scope.logResponders = responders;
                                return CortexSrv.promntForResponder(responders);
                            })
                            .then(function(response) {
                                if(response && _.isString(response)) {
                                    NotificationSrv.log(response, 'warning');
                                } else {
                                    return CortexSrv.runResponder(response.id, response.name, 'case_task_log', _.pick(taskLog, '_id'));
                                }
                            })
                            .then(function(response){
                                NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on task log'].join(' '), 'success');
                            })
                            .catch(function(err) {
                                if(err && !_.isString(err)) {
                                    NotificationSrv.error('logEntry', err.data, err.status);
                                }
                            });
                    };

                    $scope.getActions = function(logId) {
                        $scope.actions = new PaginatedQuerySrv({
                            name: 'task-log-actions',
                            version: 'v1',
                            scope: $scope,
                            streamObjectType: 'action',
                            loadAll: true,
                            sort: ['-startDate'],
                            pageSize: 100,
                            operations: [
                                { '_name': 'getLog', 'idOrName': logId },
                                { '_name': 'actions' }
                            ],
                            guard: function(updates) {
                                return _.find(updates, function(item) {
                                    return (item.base.details.objectType === 'Log') && (item.base.details.objectId === logId);
                                }) !== undefined;
                            }
                        });
                    };
                },
                link: function(scope) {

                    // drop log
                    scope.dropLog = function() {
                        scope.deleteModal = $uibModal.open({
                            scope: scope,
                            templateUrl: 'views/directives/log-entry-delete.html',
                            size: ''
                        });
                    };

                    scope.confirmDropLog = function() {
                        TaskLogSrv.delete({
                            logId: scope.log._id
                        }).$promise.then(function() {
                            scope.deleteModal.dismiss();
                        });
                    };

                    scope.cancelDropLog = function() {
                        scope.deleteModal.dismiss();
                    };

                    scope.updateLog = function() {
                        return TaskLogSrv.update({
                            logId: scope.log._id
                        }, {message: scope.log.message}, function() {}, function(response) {
                            NotificationSrv.error('CaseTaskLog', response.data, response.status);
                        });
                    };

                    scope.isImage = function(contentType) {
                        return angular.isString(contentType) && contentType.indexOf('image') === 0;
                    };
                    scope.showImage = function() {
                        var fileName = HtmlSanitizer.sanitize(scope.log.attachment.name);
                        var fileId = HtmlSanitizer.sanitize(scope.log.attachment.id);

                        $uibModal.open({
                            template: '<img style="width:100%" src="./api/datastore/' + fileId + '" alt="' + fileName + '"></img>',
                            size: 'lg'
                        });
                    };
                    scope.getUserInfo = UserSrv.getCache;
                },
                restrict: 'EA',
                scope: {
                    log: '=',
                    appConfig: '=',
                    permissions: '='
                }
            };
        });
})();
