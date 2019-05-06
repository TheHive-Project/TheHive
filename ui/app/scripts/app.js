angular.module('theHiveControllers', []);
angular.module('theHiveServices', []);
angular.module('theHiveFilters', []);
angular.module('theHiveDirectives', []);

angular.module('thehive', [
    'ngAnimate',
    'ngMessages',
    'ngSanitize',
    'ui.bootstrap',
    'ui.router',
    'ui.sortable',
    'timer',
    'angularMoment',
    'ngCsv',
    'ngTagsInput',
    'ngResource',
    'ui-notification',
    'angular-clipboard',
    'LocalStorageModule',
    'angular-markdown-editor',
    'hc.marked',
    'hljs',
    'ui.ace',
    'angular-page-loader',
    'naif.base64',
    'images-resizer',
    'duScroll',
    'dndLists',
    'colorpicker.module',
    'theHiveControllers',
    'theHiveServices',
    'theHiveFilters',
    'theHiveDirectives'
    ])
    .config(function($resourceProvider) {
        'use strict';

        $resourceProvider.defaults.stripTrailingSlashes = true;
    })
    .config(function($compileProvider) {
        'use strict';
        $compileProvider.debugInfoEnabled(false);
    })
    .config(function($stateProvider, $urlRouterProvider) {
        'use strict';

        $urlRouterProvider.otherwise('/cases');

        $stateProvider
            .state('login', {
                url: '/login',
                controller: 'AuthenticationCtrl',
                templateUrl: 'views/login.html',
                resolve: {
                    appConfig: function(VersionSrv) {
                       return VersionSrv.get();
                    }
                },
                params: {
                    autoLogin: false
                },
                title: 'Login'
            })
            .state('live', {
                url: '/live',
                templateUrl: 'views/partials/live.html',
                controller: 'LiveCtrl',
                title: 'Live feed'
            })
            .state('maintenance', {
                url: '/maintenance',
                templateUrl: 'views/maintenance.html',
                controller: 'MigrationCtrl',
                title: 'Database migration'
            })
            .state('app', {
                url: '/',
                abstract: true,
                templateUrl: 'views/app.html',
                controller: 'RootCtrl',
                resolve: {
                    currentUser: function($q, $state, AuthenticationSrv) {
                        var deferred = $q.defer();

                        AuthenticationSrv.current(function(userData) {
                            return deferred.resolve(userData);
                        }, function(err, status) {
                            return deferred.resolve(status === 520 ? status : null);
                        });

                        return deferred.promise;
                    },
                    appConfig: function(VersionSrv) {
                        return VersionSrv.get();
                    },
                    appLayout: function($q, $rootScope, AppLayoutSrv) {
                        AppLayoutSrv.init();
                        return $q.resolve();
                    },
                    uiConfig: function($q, UiSettingsSrv) {
                        UiSettingsSrv.all();
                        return $q.resolve();
                    }
                }
            })
            .state('app.main', {
                url: 'main/{viewId}',
                params: {
                    viewId: 'mytasks'
                },
                templateUrl: 'views/app.main.html',
                controller: 'MainPageCtrl'
            })
            .state('app.cases', {
                url: 'cases',
                templateUrl: 'views/partials/case/case.list.html',
                controller: 'CaseListCtrl',
                controllerAs: '$vm',
                title: 'Cases'
            })
            .state('app.search', {
                url: 'search?q',
                templateUrl: 'views/partials/search/list.html',
                controller: 'SearchCtrl',
                title: 'Search',
                resolve: {
                    metadata: function($q, DashboardSrv, NotificationSrv) {
                        var defer = $q.defer();

                        DashboardSrv.getMetadata()
                            .then(function(response) {
                                defer.resolve(response);
                            }, function(err) {
                                NotificationSrv.error('DashboardViewCtrl', err.data, err.status);
                                defer.reject(err);
                            });

                        return defer.promise;
                    }
                }
            })
            .state('app.settings', {
                url: 'settings',
                templateUrl: 'views/partials/personal-settings.html',
                controller: 'SettingsCtrl',
                title: 'Personal settings',
                resolve: {
                    currentUser: function($q, $state, $timeout, AuthenticationSrv) {
                        var deferred = $q.defer();

                        AuthenticationSrv.current(function(userData) {
                            return deferred.resolve(userData);
                        }, function( /*err, status*/ ) {

                            $timeout(function() {
                                $state.go('login');
                            });

                            return deferred.reject();
                        });

                        return deferred.promise;
                    },
                    appConfig: function(VersionSrv) {
                        return VersionSrv.get();
                    }
                }
            })
            .state('app.administration', {
                abstract: true,
                url: 'administration',
                template: '<ui-view/>',
                onEnter: function($state, AuthenticationSrv) {
                    var currentUser = AuthenticationSrv.currentUser;

                    if (!currentUser || !currentUser.roles || _.map(currentUser.roles, function(role) {
                            return role.toLowerCase();
                        }).indexOf('admin') === -1) {
                        if (!$state.is('app.cases')) {
                            $state.go('app.cases');
                        } else {
                            return $state.reload();
                        }
                    }

                    return true;
                }
            })
            .state('app.administration.users', {
                url: '/users',
                templateUrl: 'views/partials/admin/users.html',
                controller: 'AdminUsersCtrl',
                title: 'Users administration',
                resolve: {
                    appConfig: function(VersionSrv) {
                        return VersionSrv.get();
                    }
                }
            })
            .state('app.administration.case-templates', {
                url: '/case-templates',
                templateUrl: 'views/partials/admin/case-templates.html',
                controller: 'AdminCaseTemplatesCtrl',
                controllerAs: '$vm',
                title: 'Templates administration',
                resolve: {
                    templates: function(CaseTemplateSrv) {
                        return CaseTemplateSrv.list();
                    },
                    fields: function(CustomFieldsCacheSrv){
                        return CustomFieldsCacheSrv.all();
                    }
                }
            })
            .state('app.administration.report-templates', {
                url: '/report-templates',
                templateUrl: 'views/partials/admin/report-templates.html',
                controller: 'AdminReportTemplatesCtrl',
                controllerAs: 'vm',
                title: 'Report templates administration'
            })
            .state('app.administration.metrics', {
                url: '/metrics',
                templateUrl: 'views/partials/admin/metrics.html',
                controller: 'AdminMetricsCtrl',
                title: 'Metrics administration'
            })
            .state('app.administration.custom-fields', {
                url: '/custom-fields',
                templateUrl: 'views/partials/admin/custom-fields.html',
                controller: 'AdminCustomFieldsCtrl',
                controllerAs: '$vm',
                title: 'Custom fields administration'
            })
            .state('app.administration.observables', {
                url: '/observables',
                templateUrl: 'views/partials/admin/observables.html',
                controller: 'AdminObservablesCtrl',
                title: 'Observable administration'
            })
            .state('app.administration.ui-settings', {
                url: '/ui-settings',
                templateUrl: 'views/partials/admin/ui-settings.html',
                controller: 'AdminUiSettingsCtrl',
                controllerAs: '$vm',
                title: 'UI settings',
                resolve: {
                    uiConfig: function(UiSettingsSrv) {
                        return UiSettingsSrv.all();
                    }
                }
            })
            .state('app.case', {
                abstract: true,
                url: 'case/{caseId}',
                templateUrl: 'views/app.case.html',
                controller: 'CaseMainCtrl',
                title: 'Case',
                resolve: {
                    caze: function($q, $rootScope, $stateParams, CaseSrv, NotificationSrv) {
                        var deferred = $q.defer();

                        CaseSrv.get({
                            'caseId': $stateParams.caseId,
                            'nstats': true
                        }, function(data) {

                            deferred.resolve(data);

                        }, function(response) {
                            deferred.reject(response);

                            NotificationSrv.error('CaseMainCtrl', response.data, response.status);
                        });

                        return deferred.promise;
                    }
                }
            })
            .state('app.case.details', {
                url: '/details',
                templateUrl: 'views/partials/case/case.details.html',
                controller: 'CaseDetailsCtrl',
                data: {
                    tab: 'details'
                }
            })
            .state('app.case.tasks', {
                url: '/tasks',
                templateUrl: 'views/partials/case/case.tasks.html',
                controller: 'CaseTasksCtrl',
                data: {
                    tab: 'tasks'
                }
            })
            .state('app.case.links', {
                url: '/links',
                templateUrl: 'views/partials/case/case.links.html',
                controller: 'CaseLinksCtrl'
            })
            .state('app.case.alerts', {
                url: '/alerts',
                templateUrl: 'views/partials/case/case.alerts.html',
                controller: 'CaseAlertsCtrl',
                resolve: {
                    alerts: function($stateParams, CaseSrv) {
                        return CaseSrv.alerts({range: 'all'}, {
                            query: {
                              case: $stateParams.caseId
                            }
                        }).$promise;
                    }
                }
            })
            .state('app.case.tasks-item', {
                url: '/tasks/{itemId}',
                templateUrl: 'views/partials/case/case.tasks.item.html',
                controller: 'CaseTasksItemCtrl',
                resolve: {
                    task: function($q, $stateParams, CaseTaskSrv, NotificationSrv) {
                        var deferred = $q.defer();

                        CaseTaskSrv.get({
                            'taskId': $stateParams.itemId
                        }, function(data) {
                            deferred.resolve(data);
                        }, function(response) {
                            deferred.reject(response);
                            NotificationSrv.error('taskDetails', response.data, response.status);
                        });

                        return deferred.promise;
                    }
                }
            })
            .state('app.case.observables', {
                url: '/observables',
                templateUrl: 'views/partials/case/case.observables.html',
                controller: 'CaseObservablesCtrl',
                data: {
                    tab: 'observables'
                }
            })
            .state('app.case.observables-item', {
                url: '/observables/{itemId}',
                templateUrl: 'views/partials/case/case.observables.item.html',
                controller: 'CaseObservablesItemCtrl',
                resolve: {
                    appConfig: function(VersionSrv) {
                        return VersionSrv.get();
                    },
                    artifact: function($q, $stateParams, CaseArtifactSrv, NotificationSrv) {
                        var deferred = $q.defer();

                        CaseArtifactSrv.api().get({
                            'artifactId': $stateParams.itemId
                        }).$promise.then(function(data) {
                            deferred.resolve(data);
                        }).catch(function(response) {
                            deferred.reject(response);
                            NotificationSrv.error('Observable Details', response.data, response.status);
                        });

                        return deferred.promise;
                    }
                }
            })
            .state('app.alert-list', {
                url: 'alert/list',
                templateUrl: 'views/partials/alert/list.html',
                controller: 'AlertListCtrl',
                controllerAs: '$vm'
            })
            .state('app.dashboards', {
                url: 'dashboards',
                templateUrl: 'views/partials/dashboard/list.html',
                controller: 'DashboardsCtrl',
                controllerAs: '$vm'
            })
            .state('app.dashboards-view', {
                url: 'dashboards/{id}',
                templateUrl: 'views/partials/dashboard/view.html',
                controller: 'DashboardViewCtrl',
                controllerAs: '$vm',
                resolve: {
                    dashboard: function(NotificationSrv, DashboardSrv, $stateParams, $q) {
                        var defer = $q.defer();

                        DashboardSrv.get($stateParams.id)
                            .then(function(response) {
                                defer.resolve(response.data);
                            }, function(err) {
                                NotificationSrv.error('DashboardViewCtrl', err.data, err.status);
                                defer.reject(err);
                            });

                        return defer.promise;
                    },
                    metadata: function($q, DashboardSrv, NotificationSrv) {
                        var defer = $q.defer();

                        DashboardSrv.getMetadata()
                            .then(function(response) {
                                defer.resolve(response);
                            }, function(err) {
                                NotificationSrv.error('DashboardViewCtrl', err.data, err.status);
                                defer.reject(err);
                            });

                        return defer.promise;
                    }
                }
            });
    })
    .config(function($httpProvider) {
        'use strict';

        $httpProvider.defaults.xsrfCookieName = 'THE-HIVE-XSRF-TOKEN';
        $httpProvider.defaults.xsrfHeaderName = 'X-THE-HIVE-XSRF-TOKEN';
        $httpProvider.interceptors.push(function($rootScope, $q) {
            var isApiCall = function(url) {
                return url && url.startsWith('./api') && !url.startsWith('./api/stream');
            };

            return {
                request: function(config) {
                    if (isApiCall(config.url)) {
                        $rootScope.async += 1;
                    }
                    return config;
                },
                response: function(response) {
                    if (isApiCall(response.config.url)) {
                        $rootScope.async -= 1;
                    }
                    return response;
                },
                responseError: function(rejection) {
                    if (isApiCall(rejection.config.url)) {
                        $rootScope.async -= 1;
                    }
                    return $q.reject(rejection);
                }
            };
        });
    })
    .config(function(localStorageServiceProvider) {
        'use strict';

        localStorageServiceProvider
            .setPrefix('th')
            .setStorageType('localStorage')
            .setNotify(false, false);
    })
    .config(function(NotificationProvider) {
        'use strict';

        NotificationProvider.setOptions({
            delay: 10000,
            startTop: 20,
            startRight: 10,
            verticalSpacing: 20,
            horizontalSpacing: 20,
            positionX: 'left',
            positionY: 'bottom'
        });
    })
    .config(function($provide, markedProvider, hljsServiceProvider) {
        'use strict';

        markedProvider.setOptions({
            gfm: true,
            tables: true,
            sanitize: true,
            highlight: function(code, lang) {
                if (lang) {
                    return hljs.highlight(lang, code, true).value;
                } else {
                    return hljs.highlightAuto(code).value;
                }
            }
        });

        // highlight config
        hljsServiceProvider.setOptions({
            tabReplace: '    '
        });

        // Decorate the marked service to allow generating links with _target="blank"
        $provide.decorator('marked', [
            '$delegate',
            function markedDecorator($delegate) {
              // Credits: https://github.com/markedjs/marked/issues/655#issuecomment-383226346
              var defaults = markedProvider.defaults;

              var renderer = defaults.renderer;
              var linkRenderer = _.wrap(renderer.link, function(originalLink, href, title, text) {
                  var html = originalLink.call(renderer, href, title, text);
                  return html.replace(/^<a /, '<a target="_blank" rel="nofollow" ');
              });

              // Customize the link renderer
              defaults.renderer.link = linkRenderer;

              // Patch the marked instance
              $delegate.setOptions(defaults);

              return $delegate;
            }
        ]);
    })
    .run(function($rootScope) {
        'use strict';
        $rootScope.async = 0;

        $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams) {
            if (_.isFunction(toState.title)) {
                $rootScope.title = toState.title(toParams);
            } else {
                $rootScope.title = toState.title;
            }
        });
    })
    .constant('UrlParser', url);
