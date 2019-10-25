(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CaseListCtrl', CaseListCtrl);

    function CaseListCtrl($scope, $q, $state, $window, FilteringSrv, StreamStatSrv, PSearchSrv, EntitySrv, TagSrv, UserSrv, AuthenticationSrv, CaseResolutionStatus, NotificationSrv, Severity, Tlp, CortexSrv) {
        var self = this;

        this.openEntity = EntitySrv.open;
        this.getUserInfo = UserSrv.getCache;
        this.CaseResolutionStatus = CaseResolutionStatus;
        this.caseResponders = null;

        this.lastQuery = null;

        this.$onInit = function() {
            self.filtering = new FilteringSrv('case', 'case.list', {
                defaults: {
                    showFilters: true,
                    showStats: false,
                    pageSize: 15,
                    sort: ['-flag', '-startDate']
                },
                defaultFilter: [{
                    field: 'status',
                    type: 'enumeration',
                    value: {
                        list: [{
                            text: 'Open',
                            label: 'Open'
                        }]
                    }
                }]
            });

            self.filtering.initContext('list')
                .then(function() {
                    self.load();

                    $scope.$watch('$vm.list.pageSize', function (newValue) {
                        self.filtering.setPageSize(newValue);
                    });
                });

            this.caseStats = StreamStatSrv({
                scope: $scope,
                rootId: 'any',
                query: {},
                result: {},
                objectType: 'case',
                field: 'status'
            });
        };

        this.load = function() {
            this.list = PSearchSrv(undefined, 'case', {
                scope: $scope,
                filter: this.filtering.buildQuery(),
                loadAll: false,
                sort: self.filtering.context.sort,
                pageSize: self.filtering.context.pageSize,
                nstats: true
            });
        };

        this.toggleStats = function () {
            this.filtering.toggleStats();
        };

        this.toggleFilters = function () {
            this.filtering.toggleFilters();
        };

        this.filter = function () {
            self.filtering.filter().then(this.applyFilters);
        };

        this.clearFilters = function () {
            this.filtering.clearFilters()
                .then(self.search);
        };

        this.removeFilter = function (index) {
            self.filtering.removeFilter(index)
                .then(self.search);
        };

        this.search = function () {
            self.load();
            self.filtering.storeContext();
        };
        this.addFilterValue = function (field, value) {
            this.filtering.addFilterValue(field, value);
            this.search();
        };

        this.filterMyCases = function() {
            this.filtering.clearFilters()
                .then(function() {
                    var currentUser = AuthenticationSrv.currentUser;
                    self.filtering.addFilter({
                        field: 'owner',
                        type: 'user',
                        value: {
                            list: [{
                                text: currentUser.login,
                                label: currentUser.name
                            }]
                        }
                    });
                    self.search();
                });
        };

        this.filterMyOpenCases = function() {
            this.filtering.clearFilters()
                .then(function() {
                    var currentUser = AuthenticationSrv.currentUser;
                    self.filtering.addFilter({
                        field: 'owner',
                        type: 'user',
                        value: {
                            list: [{
                                text: currentUser.login,
                                label: currentUser.name
                            }]
                        }
                    });
                    self.addFilterValue('status', 'Open');
                });
        };

        this.filterByStatus = function(status) {
            this.filtering.clearFilters()
                .then(function() {
                    self.addFilterValue('status', status);
                });
        };

        this.sortBy = function(sort) {
            this.list.sort = sort;
            this.list.update();
            this.filtering.setSort(sort);
        };

        this.getCaseResponders = function(caseId, force) {
            if (!force && this.caseResponders !== null) {
                return;
            }

            this.caseResponders = null;
            CortexSrv.getResponders('case', caseId)
                .then(function(responders) {
                    self.caseResponders = responders;
                })
                .catch(function(err) {
                    NotificationSrv.error('CaseList', err.data, err.status);
                });
        };

        this.runResponder = function(responderId, responderName, caze) {
            CortexSrv.runResponder(responderId, responderName, 'case', _.pick(caze, 'id', 'tlp', 'pap'))
                .then(function(response) {
                    NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on case', caze.title].join(' '), 'success');
                })
                .catch(function(response) {
                    if (response && !_.isString(response)) {
                        NotificationSrv.error('CaseList', response.data, response.status);
                    }
                });
        };

    }
})();
