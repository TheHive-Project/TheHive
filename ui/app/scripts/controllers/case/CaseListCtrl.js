(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CaseListCtrl', CaseListCtrl);

    function CaseListCtrl($scope, $q, $state, $window, CasesUISrv, StreamStatSrv, PSearchSrv, EntitySrv, UserInfoSrv, TagSrv, UserSrv, AuthenticationSrv, CaseResolutionStatus) {
        var self = this;

        this.showFlow = true;
        this.openEntity = EntitySrv.open;
        this.getUserInfo = UserInfoSrv;
        this.CaseResolutionStatus = CaseResolutionStatus;

        this.uiSrv = CasesUISrv;
        this.uiSrv.initContext('list');
        this.searchForm = {
            searchQuery: this.uiSrv.buildQuery() || ''
        };

        this.list = PSearchSrv(undefined, 'case', {
            filter: self.searchForm.searchQuery !== '' ? {
                _string: self.searchForm.searchQuery
            } : '',
            loadAll: false,
            sort: self.uiSrv.context.sort,
            pageSize: self.uiSrv.context.pageSize,
            nstats: true
        });

        this.caseStats = StreamStatSrv({
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
            self.search();
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

        this.getStatuses = function() {
            return $q.resolve([
                {text: 'Open'},
                {text: 'Resolved'}
            ]);
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

        this.filterByStatus = function(status) {
            this.uiSrv.clearFilters()
                .then(function(){
                    self.addFilterValue('status', status);
                });
        };

        this.sortBy = function(sort) {
            this.list.sort = sort;
            this.list.update();
            this.uiSrv.setSort(sort);
        };

        this.live = function() {
            $window.open($state.href('live'), 'TheHiveLive',
                'width=500,height=700,menubar=no,status=no,toolbar=no,location=no,scrollbars=yes');
        };

    }
})();
