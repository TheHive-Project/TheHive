(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CaseListCtrl', CaseListCtrl);

    function CaseListCtrl($scope, $q, $state, $window, CasesUISrv, StreamStatSrv, PSearchSrv, EntitySrv, UserInfoSrv, TagSrv, UserSrv, AuthenticationSrv, CaseResolutionStatus, NotificationSrv, Severity, Tlp, CortexSrv) {
        var self = this;

        this.openEntity = EntitySrv.open;
        this.getUserInfo = UserInfoSrv;
        this.CaseResolutionStatus = CaseResolutionStatus;
        this.caseResponders = null;

        this.uiSrv = CasesUISrv;
        this.uiSrv.initContext('list');
        this.searchForm = {
            searchQuery: this.uiSrv.buildQuery() || ''
        };
        this.lastQuery = null;

        this.list = PSearchSrv(undefined, 'case', {
            scope: $scope,
            filter: self.searchForm.searchQuery !== '' ? {
                _string: self.searchForm.searchQuery
            } : '',
            loadAll: false,
            sort: self.uiSrv.context.sort,
            pageSize: self.uiSrv.context.pageSize,
            nstats: true
        });

        this.caseStats = StreamStatSrv({
            scope: $scope,
            rootId: 'any',
            query: {},
            result: {},
            objectType: 'case',
            field: 'status'
        });

        $scope.$watch('$vm.list.pageSize', function (newValue) {
            self.uiSrv.setPageSize(newValue);
        });

        this.toggleStats = function () {
            this.uiSrv.toggleStats();
        };

        this.toggleFilters = function () {
            this.uiSrv.toggleFilters();
        };

        this.filter = function () {
            this.uiSrv.filter().then(this.applyFilters);
        };

        this.applyFilters = function () {
            self.searchForm.searchQuery = self.uiSrv.buildQuery();

            if(self.lastQuery !== self.searchForm.searchQuery) {
                self.lastQuery = self.searchForm.searchQuery;
                self.search();
            }

        };

        this.clearFilters = function () {
            this.uiSrv.clearFilters().then(this.applyFilters);
        };

        this.addFilter = function (field, value) {
            this.uiSrv.addFilter(field, value).then(this.applyFilters);
        };

        this.removeFilter = function (field) {
            this.uiSrv.removeFilter(field).then(this.applyFilters);
        };

        this.search = function () {
            this.list.filter = {
                _string: this.searchForm.searchQuery
            };

            this.list.update();
        };
        this.addFilterValue = function (field, value) {
            var filterDef = this.uiSrv.filterDefs[field];
            var filter = this.uiSrv.activeFilters[field];
            var date;

            if (filter && filter.value) {
                if (filterDef.type === 'list') {
                    if (_.pluck(filter.value, 'text').indexOf(value) === -1) {
                        filter.value.push({
                            text: value
                        });
                    }
                } else if (filterDef.type === 'date') {
                    date = moment(value);
                    this.uiSrv.activeFilters[field] = {
                        value: {
                            from: date.hour(0).minutes(0).seconds(0).toDate(),
                            to: date.hour(23).minutes(59).seconds(59).toDate()
                        }
                    };
                } else {
                    filter.value = value;
                }
            } else {
                if (filterDef.type === 'list') {
                    this.uiSrv.activeFilters[field] = {
                        value: [{
                            text: value
                        }]
                    };
                } else if (filterDef.type === 'date') {
                    date = moment(value);
                    this.uiSrv.activeFilters[field] = {
                        value: {
                            from: date.hour(0).minutes(0).seconds(0).toDate(),
                            to: date.hour(23).minutes(59).seconds(59).toDate()
                        }
                    };
                } else {
                    this.uiSrv.activeFilters[field] = {
                        value: value
                    };
                }
            }

            this.filter();
        };

        this.getStatuses = function(query) {
            var defer = $q.defer();

            $q.resolve([
                {text: 'Open'},
                {text: 'Resolved'},
                {text: 'Hold'},
                {text: 'Review'},
                {text: 'Pending'}
            ]).then(function(response) {
                var statuses = [];

                statuses = _.filter(response, function(stat) {
                    var regex = new RegExp(query, 'gi');
                    return regex.test(stat.text);
                });

                defer.resolve(statuses);
            });

            return defer.promise;

        };

        this.getSeverities = function(query) {
            var defer = $q.defer();

            $q.resolve(_.map(Severity.keys, function(value, key) {
                return {text: key};
            })).then(function(response) {
                var severities = [];

                severities = _.filter(response, function(sev) {
                    var regex = new RegExp(query, 'gi');
                    return regex.test(sev.text);
                });

                defer.resolve(severities);
            });

            return defer.promise;
        };

        this.getTags = function(query) {
            return TagSrv.fromCases(query);
        };

        this.getUsers = function(query) {
            return UserSrv.list({
                _and: [
                    {
                        status: 'Ok'
                    }
                ]
            }).then(function(data) {
                return _.map(data, function(user) {
                    return {
                        label: user.name,
                        text: user.id
                    };
                });
            }).then(function(users) {
                var filtered = _.filter(users, function(user) {
                    var regex = new RegExp(query, 'gi');
                    return regex.test(user.label);
                });

                return filtered;
            });
        };

        this.filterMyCases = function() {
            this.uiSrv.clearFilters()
                .then(function(){
                    var currentUser = AuthenticationSrv.currentUser;
                    self.uiSrv.activeFilters.owner = {
                        value: [{
                            text: currentUser.id,
                            label: currentUser.name
                        }]
                    };
                    self.filter();
                });
        };

        this.filterMyOpenCases = function() {
            this.uiSrv.clearFilters()
                .then(function(){
                    var currentUser = AuthenticationSrv.currentUser;
                    self.uiSrv.activeFilters.owner = {
                        value: [{
                            text: currentUser.id,
                            label: currentUser.name
                        }]
                    };
                    self.filter();
                    self.addFilterValue('status', 'Open');
                });
        };

        this.filterByStatus = function(status) {
            this.uiSrv.clearFilters()
                .then(function(){
                    self.addFilterValue('status', status);
                });
        };

        this.filterBySeverity = function(numericSev) {
            self.addFilterValue('severity', Severity.values[numericSev]);
        };

        this.filterByTlp = function(value) {
            self.addFilterValue('tlp', Tlp.values[value]);
        };

        this.sortBy = function(sort) {
            this.list.sort = sort;
            this.list.update();
            this.uiSrv.setSort(sort);
        };

        this.getCaseResponders = function(caseId, force) {
            if(!force && this.caseResponders !== null) {
               return;
            }

            this.caseResponders = null;
            CortexSrv.getResponders('case', caseId)
              .then(function(responders) {
                  self.caseResponders = responders;
              })
              .catch(function(err) {
                  NotificationSrv.error('CaseList', response.data, response.status);
              })
        };

        this.runResponder = function(responderId, responderName, caze) {
            CortexSrv.runResponder(responderId, responderName, 'case', _.pick(caze, 'id', 'tlp', 'pap'))
              .then(function(response) {
                  NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on case', caze.title].join(' '), 'success');
              })
              .catch(function(response) {
                  if(response && !_.isString(response)) {
                      NotificationSrv.error('CaseList', response.data, response.status);
                  }
              });
        };

    }
})();
