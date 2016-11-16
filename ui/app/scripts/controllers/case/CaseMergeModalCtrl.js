(function() {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseMergeModalCtrl', CaseMergeModalCtrl);

    function CaseMergeModalCtrl($state, $modalInstance, SearchSrv, CaseSrv, UserInfoSrv, AlertSrv, caze) {
        var me = this;

        this.caze = caze;
        this.search = {
            caseId: null,
            cases: []
        };
        this.getUserInfo = UserInfoSrv;

        this.getCaseByNumber = function() {
            if (this.search.caseId && this.search.caseId !== this.caze.caseId) {
                SearchSrv(function(data /*, total*/ ) {
                    console.log(data);
                    me.search.cases = data;
                }, {
                    _string: 'caseId:' + me.search.caseId
                }, 'case', 'all');
            } else {
                this.search.cases = [];
            }
        };

        this.merge = function() {
            // TODO pass params as path params not query params
            CaseSrv.merge({}, {
                caseId: me.caze.id,
                mergedCaseId: me.search.cases[0].id
            }, function(merged) {

                $state.go('app.case.details', {
                    caseId: merged.id
                });

                $modalInstance.dismiss();

                AlertSrv.log('The cases have been successfully merged into a new case #' + merged.caseId, 'success');
            }, function(response){
                AlertSrv.error('CaseMergeModalCtrl', response.data, response.status);
            });
        };

        this.cancel = function() {
            $modalInstance.dismiss();
        };
    }
})();
