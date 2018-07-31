(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('logEntry', function($uibModal, TaskLogSrv, UserInfoSrv, NotificationSrv) {
            return {
                templateUrl: 'views/directives/log-entry.html',
                controller: function($scope, CortexSrv, PSearchSrv) {
                    $scope.showActions = false;
                    $scope.actions = null;
                    $scope.logResponders = null;
                    $scope.getLogResponders = function(logId) {
                        $scope.logResponders = null;
                        CortexSrv.getResponders('case_task_log', logId)
                            .then(function(responders) {
                                $scope.logResponders = responders;
                            })
                            .catch(function(err) {
                                NotificationSrv.error('logEntry', response.data, response.status);
                            })
                    };

                    $scope.runResponder = function(responderId, log) {
                        CortexSrv.runResponder(responderId, 'case_task_log', _.pick(log, 'id'))
                          .then(function(response) {
                              NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on task log'].join(' '), 'success');
                          })
                          .catch(function(response) {
                              NotificationSrv.error('logEntry', response.data, response.status);
                          });
                    };

                    $scope.getActions = function(logId) {
                        $scope.actions = PSearchSrv(null, 'connector/cortex/action', {
                            scope: $scope,
                            streamObjectType: 'action',
                            filter: {
                                _and: [
                                    {
                                        _not: {
                                            status: 'Deleted'
                                        }
                                    }, {
                                        objectType: 'case_task_log'
                                    }, {
                                        objectId: logId
                                    }
                                ]
                            },
                            sort: ['-startDate'],
                            pageSize: 100,
                            guard: function(updates) {
                                return _.find(updates, function(item) {
                                    return (item.base.object.objectType === 'case_task_log') && (item.base.object.objectId === logId);
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
                            logId: scope.log.id
                        }).$promise.then(function() {
                            scope.deleteModal.dismiss();
                        });
                    };

                    scope.cancelDropLog = function() {
                        scope.deleteModal.dismiss();
                    };

                    scope.updateLog = function() {
                        return TaskLogSrv.update({
                            logId: scope.log.id
                        }, {message: scope.log.message}, function() {}, function(response) {
                            NotificationSrv.error('CaseTaskLog', response.data, response.status);
                        });
                    };

                    scope.isImage = function(contentType) {
                        return angular.isString(contentType) && contentType.indexOf('image') === 0;
                    };
                    scope.showImage = function() {
                        $uibModal.open({
                            template: '<img style="width:100%" src="./api/datastore/' + scope.log.attachment.id + '" alt="' + scope.log.attachment.name + '"></img>',
                            size: 'lg'
                        });
                    };
                    scope.getUserInfo = UserInfoSrv;
                },
                restrict: 'EA',
                scope: {
                    log: '=',
                    appConfig: '='
                }
            };
        });
})();
