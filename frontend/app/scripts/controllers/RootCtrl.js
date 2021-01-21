/**
 * Controller for main page
 */
angular.module('theHiveControllers').controller('RootCtrl',
    function($scope, $rootScope, $timeout, $uibModal, $location, $state, AuthenticationSrv, AlertingSrv, StreamSrv, StreamQuerySrv, CaseSrv, CaseTemplateSrv, CustomFieldsSrv, NotificationSrv, AppLayoutSrv, VersionSrv, currentUser, appConfig) {
        'use strict';

        if(currentUser === 520) {
            $state.go('maintenance');
            return;
        }else if(!currentUser || !currentUser.login) {
            $state.go('login', {autoLogin: appConfig.config.ssoAutoLogin });
            return;
        }

        $rootScope.layoutSrv = AppLayoutSrv;
        $scope.appConfig = appConfig;
        $scope.hasCortexConnector = VersionSrv.hasCortexConnector();

        $scope.querystring = '';
        $scope.view = {
            data: 'mytasks'
        };
        $scope.mispEnabled = false;
        $scope.customFieldsCache = [];
        $scope.currentUser = currentUser;

        StreamSrv.init();
        VersionSrv.startMonitoring(function(conf) {
          var connectors = ['misp', 'cortex'];

          _.each(connectors, function(connector) {
              var currentStatus = $scope.appConfig.connectors[connector];
              var newStatus = conf.connectors[connector];
              if(currentStatus && currentStatus.enabled === newStatus.enabled &&
                  newStatus.enabled === true &&
                  currentStatus.status !== newStatus.status) {

                  if(newStatus.status === 'OK') {
                      NotificationSrv.log('The configured ' + connector.toUpperCase() + ' connections are now up.', 'success');
                  } else if(newStatus.status === 'WARNING') {
                      NotificationSrv.log('Some of the configured ' + connector.toUpperCase() + ' connections have errors. Please check your configuration.', 'warning');
                  } else {
                      NotificationSrv.log('The configured ' + connector.toUpperCase() + ' connections have errors. Please check your configuration.', 'error');
                  }
              }
          });

          $scope.appConfig = conf;
          $scope.hasCortexConnector = VersionSrv.hasCortexConnector();
        });

        CaseTemplateSrv.list().then(function(templates) {
            $scope.templates = templates;
        });

        StreamQuerySrv('v1', [
            {_name: 'myTasks'},
            {_name: 'filter',
                _in: {
                    _field: 'status',
                    _values: ['Waiting', 'InProgress']
                }
            },
            {_name: 'count'}
        ], {
            scope: $scope,
            rootId: 'any',
            objectType: 'case_task',
            query: {
                params: {
                    name: 'my-tasks.stats'
                }
            },
            onUpdate: function(data) {
                $scope.myCurrentTasksCount = data;
            }
        });

        StreamQuerySrv('v1', [
            {_name: 'waitingTasks'},
            {_name: 'count'}
        ], {
            scope: $scope,
            rootId: 'any',
            objectType: 'case_task',
            query: {
                params: {
                    name: 'waiting-tasks.stats'
                }
            },
            onUpdate: function(data) {
                $scope.waitingTasksCount = data;
            }
        });

        StreamQuerySrv('v1', [
            {_name: 'listAlert'},
            {_name: 'filter', _field: 'read', _value: false},
            {_name: 'count'}
        ], {
            scope: $scope,
            rootId: 'any',
            objectType: 'alert',
            query: {
                params: {
                    name: 'unread-alert-count'
                }
            },
            onUpdate: function(data) {
                $scope.unreadAlertCount = data;
            }
        });

        $scope.$on('templates:refresh', function(){
            CaseTemplateSrv.list().then(function(templates) {
                $scope.templates = templates;
            });
        });

        $scope.$on('custom-fields:refresh', function() {
            // Get custom fields cache
            $scope.initCustomFieldsCache();
        });


        // Get Alert counts
        //$scope.alertEvents = AlertingSrv.stats($scope);

        // $scope.$on('alert:event-imported', function() {
        //     $scope.alertEvents = AlertingSrv.stats($scope);
        // });

        // FIXME
        // $scope.$on('misp:status-updated', function(event, enabled) {
        //     $scope.mispEnabled = enabled;
        // });

        $scope.initCustomFieldsCache = function() {
            CustomFieldsSrv.all().then(function(list) {
                $scope.customFieldsCache = list;
            });
        };
        $scope.initCustomFieldsCache();

        $scope.isSuperAdmin = function() {
            return AuthenticationSrv.isSuperAdmin();
        };

        $scope.selectView = function(name) {
            $state.go('app.main', {
                viewId: name
            });
            $scope.view.data = name;
        };

        $scope.logout = function() {
            AuthenticationSrv.logout(function() {
                $state.go('login');
            }, function(data, status) {
                NotificationSrv.error('RootCtrl', data, status);
            });
        };

        $scope.switchOrg = function() {
            var modal = $uibModal.open({
                templateUrl: 'views/components/org/orgSwitch.modal.html',
                controller: 'OrgSwitchCtrl',
                controllerAs: '$dialog',
                resolve: {
                    currentUser: $scope.currentUser
                }
            });

            modal.result
                .then(function(organisation) {
                    $rootScope.isLoading = true;

                    return AuthenticationSrv.current(organisation)
                        .then(function(userData) {
                            $scope.currentUser = userData;
                            StreamSrv.cancelPoll();
                        });
                })
                .then(function() {
                    $state.go('app.index', {}, {reload:true});
                })
                .catch(function(err) {
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('Switch organisation', err.data, err.status);
                    }
                })
                .finally(function() {
                    $timeout(function() {
                        $rootScope.isLoading = false;
                    }, 500);

                });

        };

        $scope.createNewCase = function(template) {
            var modal = $uibModal.open({
                templateUrl: 'views/partials/case/case.creation.html',
                controller: 'CaseCreationCtrl',
                size: 'lg',
                resolve: {
                    template: template
                }
            });

            modal.result
                .then(function(data) {
                    $state.go('app.case.details', {
                        caseId: data.id
                    });
                })
                .catch(function(err) {
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('CaseCreationCtrl', err.data, err.status);
                    }
                });
        };

        $scope.openTemplateSelector = function() {
            var modal = $uibModal.open({
                templateUrl: 'views/partials/case/case.templates.selector.html',
                controller: 'CaseTemplatesDialogCtrl',
                controllerAs: 'dialog',
                size: 'lg',
                resolve: {
                    templates: function(){
                        return $scope.templates;
                    },
                    uiSettings: ['UiSettingsSrv', function(UiSettingsSrv) {
                        return UiSettingsSrv.all();
                    }]
                }
            });

            modal.result
                .then(function(template) {
                    $scope.createNewCase(template);
                })
                .catch(function(err) {
                    if(err && !_.isString(err)) {
                        NotificationSrv.error('Template Selection', err.data, err.status);
                    }
                });
        };

        $scope.aboutTheHive = function() {
            $uibModal.open({
                templateUrl: 'views/partials/about.html',
                controller: 'AboutCtrl',
                size: ''
            });
        };

        $scope.search = function(caseId) {
            if(!caseId || !_.isNumber(caseId) || caseId <= 0) {
                return;
            }

            CaseSrv.query({
                query: {
                    caseId: caseId
                },
                range: '0-1'
            }, function(response) {
                if(response.length === 1) {
                    $state.go('app.case.details', {caseId: response[0].id}, {reload: true});
                } else {
                    NotificationSrv.log('Unable to find the case with number ' + caseId, 'error');
                }
            }, function(err) {
                NotificationSrv.error('Case search', err.data, err.status);
            });
        };

        // Used to show spinning refresh icon n times
        $scope.getNumber = function(num) {
            return new Array(num);
        };
    }
);
